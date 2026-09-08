package io.github.semanticsearch.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;
import io.github.semanticsearch.service.DocumentService;
import io.github.semanticsearch.service.EmbeddingService;
import io.github.semanticsearch.service.IndexService;

/**
 * What splitting a document into passages buys, and what it obliges the write path to clean up.
 *
 * <p>The model reads 256 word pieces. A four hundred word document embedded whole is a vector for
 * its opening, and the sentence at the end is unreachable by meaning however well it answers the
 * query. Each test states the query that only the last passage can answer.
 */
@SpringBootTest(properties = "embedding.provider=onnx")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChunkedRetrievalTest {

  private static final String TITLE = "Seasonal Produce Planning";
  private static final String BURIED =
      "Trapped ion qubits hold quantum entanglement long enough to run an error corrected circuit.";
  private static final String QUERY = "quantum entanglement in trapped ion qubits";

  @Autowired private MockMvc mockMvc;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private DocumentService documentService;
  @Autowired private IndexService indexService;
  @Autowired private EmbeddingService embeddingService;
  @Autowired private CacheManager cacheManager;
  @Autowired private ObjectMapper objectMapper;

  private Document longDocument;

  @BeforeEach
  void seedCorpus() {
    documentRepository.deleteAll();
    indexService.rebuildIndex();
    clearCaches();
    longDocument = create(TITLE, produceProse() + " " + BURIED);
  }

  @Test
  void aSentencePastTheModelsWindowIsStillRetrievable() throws Exception {
    assertTrue(longDocument.getPassageCount() > 1, "the fixture has to be long enough to split");

    // The premise. Embedded whole, this document scores below the floor the API
    // applies by default, so on one vector per document the query cannot reach it.
    double wholeDocument = similarity(QUERY, TITLE + "\n" + produceProse() + " " + BURIED);
    assertTrue(wholeDocument < 0.2, "embedded whole it scores " + wholeDocument);

    JsonNode results = search(QUERY);

    assertEquals(1, results.size());
    assertEquals(TITLE, results.get(0).get("title").asText());
    assertTrue(
        results.get(0).get("score").asDouble() > wholeDocument,
        "the passage scored no better than the whole document");
  }

  @Test
  void shorteningADocumentRemovesThePassagesItNoLongerHas() throws Exception {
    assertTrue(longDocument.getPassageCount() > 1);

    Document shortened = update(longDocument.getId(), TITLE, "Tomatoes and courgettes in August.");

    assertEquals(1, shortened.getPassageCount());
    // The passages the long version left behind would keep answering this query
    // under an id whose text no longer contains a word of it.
    assertTrue(search(QUERY).isEmpty(), "a passage of the previous version is still indexed");
  }

  @Test
  void deletingADocumentRemovesEveryPassage() throws Exception {
    assertTrue(longDocument.getPassageCount() > 1);

    assertTrue(documentService.delete(longDocument.getId()));

    // Asserted against the index and not through the API. A deleted row is
    // dropped at hydration whatever the index still holds, so search cannot tell
    // an empty index from an index full of orphans.
    assertTrue(
        indexService.similarityTo(embeddingService.embed(QUERY), List.of(longDocument)).isEmpty(),
        "a passage outlived its document");
    assertTrue(search(QUERY).isEmpty());
  }

  @Test
  void aFailedEmbeddingLeavesTheIndexAsItWas() throws Exception {
    int before = longDocument.getPassageCount();
    assertTrue(before > 1);

    // Half a write is worse than none. The first passages would hold the new text
    // and the rest the old, under one id, with nothing recording the disagreement.
    Document broken = new Document();
    broken.setTitle(TITLE);
    broken.setContent("");
    broken.setMetadata(Map.of("topic", "produce"));

    assertThrows(
        Exception.class,
        () -> documentService.update(longDocument.getId(), broken),
        "an empty document should not be accepted");

    Document reloaded = documentRepository.findById(longDocument.getId()).orElseThrow();
    assertEquals(before, reloaded.getPassageCount());
  }

  @Test
  void aShortDocumentIsStillOnePassage() {
    // Chunking has to be free for a corpus that does not need it, or every
    // relevance figure measured on the demo corpus stops being comparable.
    Document brief = create("Latency Budgets", "Latency budgets keep responses under a p95.");

    assertEquals(1, brief.getPassageCount());
  }

  private double similarity(String left, String right) {
    List<Double> a = embeddingService.embed(left);
    List<Double> b = embeddingService.embed(right);
    double dot = 0.0;
    for (int i = 0; i < a.size(); i++) {
      dot += a.get(i) * b.get(i);
    }
    return dot;
  }

  private JsonNode search(String query) throws Exception {
    String body =
        mockMvc
            .perform(get("/api/v1/search").param("query", query))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body);
  }

  private Document create(String title, String content) {
    Document document = new Document();
    document.setTitle(title);
    document.setContent(content);
    document.setMetadata(Map.of("topic", "produce"));
    return documentService.create(document);
  }

  private Document update(UUID id, String title, String content) {
    Document document = new Document();
    document.setTitle(title);
    document.setContent(content);
    document.setMetadata(Map.of("topic", "produce"));
    Document updated = documentService.update(id, document).orElseThrow();
    clearCaches();
    return updated;
  }

  private void clearCaches() {
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
  }

  /** Four hundred words with nothing in them about the query. */
  private static String produceProse() {
    List<String> paragraphs = new ArrayList<>();
    for (int week = 0; week < 20; week++) {
      paragraphs.add(
          "Late summer brings a glut of tomatoes, courgettes and stone fruit, and the kitchen plans"
              + " week "
              + week
              + " of its menu around whatever the growers deliver. Preserving what cannot be served"
              + " fresh keeps the cost of the winter menu down.");
    }
    return String.join(" ", paragraphs);
  }

  @Test
  void everyPassageOfADocumentCollapsesToOneResult() {
    // Asserted on the index, because SearchService unions candidates into a set
    // and would hide a duplicate the retriever emitted. A query matching several
    // passages must answer with the document once, at its best passage's score.
    List<Double> query = embeddingService.embed("tomatoes courgettes stone fruit summer menu");
    List<UUID> retrieved =
        indexService.findSimilarDocuments(query, 10, 0.0).stream().map(Map.Entry::getKey).toList();

    assertTrue(retrieved.contains(longDocument.getId()));
    assertEquals(
        retrieved.size(),
        retrieved.stream().distinct().count(),
        "the retriever returned the same document more than once");
  }
}
