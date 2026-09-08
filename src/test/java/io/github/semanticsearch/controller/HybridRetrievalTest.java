package io.github.semanticsearch.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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

import io.github.semanticsearch.config.SearchProperties;
import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;
import io.github.semanticsearch.service.IndexService;

/**
 * The case hybrid retrieval exists for: a document the query's words point straight at and the
 * embedding model has no way to reach.
 *
 * <p>The corpus makes that gap deliberate. Two dozen short documents each carry a different error
 * code, which is what a bare code query looks like to an embedding model, so they fill the vector
 * neighbourhood. The one document holding the queried code is four hundred words about seasonal
 * produce with the code at the end, past the 256 word pieces the model reads, so nothing the model
 * sees connects it to the query. BM25 tokenizes the whole document, and the code appears in one
 * document out of twenty-five, the highest inverse document frequency this corpus offers.
 *
 * <p>The first test runs the same query with hybrid retrieval off and then on. That is the
 * difference the feature makes, measured rather than assumed: an assertion that the document comes
 * back proves nothing on its own if it was coming back all along.
 *
 * <p>The two fusion methods answer this query differently, and both answers are pinned here.
 * Recovering the document into the candidate pool is not the same as ranking it, and which method
 * ranks it depends on arithmetic rather than on preference.
 */
@SpringBootTest(properties = "embedding.provider=onnx")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HybridRetrievalTest {

  private static final String QUERY = "e1043";
  private static final String GOLD_TITLE = "Seasonal Produce Planning";

  /** Five results, so the pipeline over-fetches 25 candidates from each retriever. */
  private static final int LIMIT = 5;

  @Autowired private MockMvc mockMvc;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private IndexService indexService;
  @Autowired private CacheManager cacheManager;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SearchProperties searchProperties;

  @BeforeEach
  void seedCorpus() {
    documentRepository.deleteAll();
    indexService.rebuildIndex();
    clearCaches();

    for (int i = 0; i < 24; i++) {
      index("Error e20" + (10 + i), "Error code e20" + (10 + i) + " on the storage replica.");
    }
    index(GOLD_TITLE, longProseEndingWith(QUERY));
  }

  @AfterEach
  void restoreDefaults() {
    searchProperties.setHybridEnabled(true);
    searchProperties.setFusion("blend");
  }

  @Test
  void lexicalRetrievalRecoversADocumentTheVectorStageNeverSees() throws Exception {
    searchProperties.setHybridEnabled(false);
    clearCaches();

    assertFalse(
        titlesFor(QUERY).contains(GOLD_TITLE),
        "premise broken: vector retrieval alone already returns the gold document");

    searchProperties.setHybridEnabled(true);
    searchProperties.setFusion("rrf");
    clearCaches();

    assertTrue(
        titlesFor(QUERY).contains(GOLD_TITLE),
        "the only document holding the query term was not recovered");
  }

  @Test
  void theWeightedBlendCannotSurfaceALexicalOnlyMatchHere() throws Exception {
    // Not a defect, and worth pinning so the trade-off between the two methods
    // stays visible. The blend caps a document with no vector similarity at the
    // lexical weight, 0.3 at the defaults, while every one of the 24 decoys is a
    // close vector match and keeps around 0.75. No BM25 score can clear that.
    //
    // Reciprocal rank fusion reads positions instead, so rank 1 on the lexical
    // list stands beside rank 1 on the vector list, which is the whole reason to
    // reach for it when the two retrievers disagree this sharply.
    searchProperties.setFusion("blend");
    clearCaches();

    assertFalse(
        titlesFor(QUERY).contains(GOLD_TITLE),
        "the blend now surfaces the lexical-only match; re-measure the gold set and update this");
  }

  @Test
  void aQueryWithNoLexicalMatchStillRanksByMeaning() throws Exception {
    // Fusion must cost nothing when only one retriever has an opinion. This query
    // shares no token with any document, so the lexical list comes back empty and
    // the vector ranking carries it alone.
    JsonNode results = search("a failing disk in the storage tier");

    assertEquals(LIMIT, results.size());
    assertTrue(
        results.get(0).get("score").asDouble() > 0.2,
        "scored " + results.get(0).get("score").asDouble());
  }

  /** Titles the API returns for a query, in rank order. */
  private List<String> titlesFor(String query) throws Exception {
    List<String> titles = new ArrayList<>();
    search(query).forEach(result -> titles.add(result.get("title").asText()));
    return titles;
  }

  private JsonNode search(String query) throws Exception {
    String body =
        mockMvc
            .perform(
                get("/api/v1/search")
                    .param("query", query)
                    .param("limit", String.valueOf(LIMIT))
                    .param("minScore", "0.0"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body);
  }

  /** Both caches key on the query, which is the same across these calls while the answer is not. */
  private void clearCaches() {
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
  }

  /**
   * Four hundred words of prose with {@code term} at the end.
   *
   * <p>The model truncates at 256 word pieces, roughly two hundred words, so the term sits outside
   * anything it reads and this document's vector is decided entirely by the produce writing in
   * front of it. {@code Tokenizer} reads the whole text, so BM25 finds the term regardless. Long
   * documents open the same gap in production, which is one of the reasons to chunk them.
   */
  private static String longProseEndingWith(String term) {
    StringBuilder text = new StringBuilder();
    for (int paragraph = 0; paragraph < 20; paragraph++) {
      text.append("Late summer brings a glut of tomatoes, courgettes and stone fruit, and the ")
          .append("kitchen plans week ")
          .append(paragraph)
          .append(" of its menu around whatever the growers deliver. Preserving what cannot be ")
          .append("served fresh keeps the cost of the winter menu down. ");
    }
    return text.append("The supplier files these deliveries under ")
        .append(term)
        .append(".")
        .toString();
  }

  private UUID index(String title, String content) {
    Document document = new Document();
    document.setTitle(title);
    document.setContent(content);
    document.setContentHash(UUID.randomUUID().toString());
    document.setMetadata(Map.of("topic", "operations"));
    Document saved = documentRepository.save(document);
    indexService.indexDocument(saved);
    return saved.getId();
  }
}
