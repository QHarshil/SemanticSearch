package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;

/**
 * Indexing and nearest-neighbour lookup against the in-memory index.
 *
 * <p>Scores are asserted against the value the geometry implies, 1.0 for a vector compared with
 * itself and strictly between 0 and 1 for a partial overlap, so a similarity regression moves an
 * assertion rather than staying inside a loose bound.
 */
@SpringBootTest
@ActiveProfiles("test")
class IndexServiceTest {

  @Autowired private IndexService indexService;
  @Autowired private EmbeddingService embeddingService;
  @Autowired private DocumentRepository documentRepository;

  @BeforeEach
  void resetCorpus() {
    documentRepository.deleteAll();
    indexService.rebuildIndex();
  }

  private Document index(String title, String content) {
    Document document = new Document();
    document.setTitle(title);
    document.setContent(content);
    document.setContentHash(hash(content));
    return indexService.indexDocument(documentRepository.save(document));
  }

  private List<Map.Entry<UUID, Double>> search(String query, int limit, double minScore) {
    return indexService.findSimilarDocuments(embeddingService.embed(query), limit, minScore);
  }

  @Test
  void similarityToAgreesWithWhatTheKnnSearchReports() {
    // Hybrid retrieval scores candidates the kNN search never returned, so the two
    // paths have to produce the same number for the same document. If they drift,
    // one fused result list carries scores from two scales.
    Document match = index("Vector Search", "Vector search compares embeddings.");
    Document unrelated = index("Bread Baking", "Sourdough needs a long cold proof.");
    List<Double> queryVector = embeddingService.embed("vector search embeddings");

    Map<UUID, Double> scores =
        indexService.similarityTo(queryVector, List.of(match.getId(), unrelated.getId()));

    assertEquals(2, scores.size());
    double fromKnn =
        indexService.findSimilarDocuments(queryVector, 10, 0.0).stream()
            .filter(hit -> hit.getKey().equals(match.getId()))
            .findFirst()
            .orElseThrow()
            .getValue();
    assertEquals(fromKnn, scores.get(match.getId()), 1e-12);
    assertTrue(
        scores.get(match.getId()) > scores.get(unrelated.getId()),
        "matching " + scores.get(match.getId()) + ", unrelated " + scores.get(unrelated.getId()));
  }

  @Test
  void similarityToOmitsIdsTheIndexDoesNotHold() {
    // Absent rather than zero, so a caller can tell "not indexed" apart from
    // "indexed and unrelated" and decide what to do about it.
    Document indexed = index("Vector Search", "Vector search compares embeddings.");

    Map<UUID, Double> scores =
        indexService.similarityTo(
            embeddingService.embed("vector search"), List.of(indexed.getId(), UUID.randomUUID()));

    assertEquals(Set.of(indexed.getId()), scores.keySet());
  }

  @Test
  void similarityToScoresADocumentAgainstItsOwnVectorAtOne() {
    Document indexed = index("Latency Budgets", "Latency budgets keep responses under a p95.");

    Map<UUID, Double> scores =
        indexService.similarityTo(
            embeddingService.embed("Latency Budgets\nLatency budgets keep responses under a p95."),
            List.of(indexed.getId()));

    assertEquals(1.0, scores.get(indexed.getId()), 1e-9);
  }

  @Test
  void indexingMarksTheDocumentAndAssignsAVectorId() {
    Document indexed = index("Doc One", "Semantic vector search over documents.");

    assertTrue(indexed.isIndexed());
    assertNotNull(indexed.getVectorId());
    assertEquals(
        indexed.getVectorId(),
        documentRepository.findById(indexed.getId()).orElseThrow().getVectorId(),
        "the vector id must be persisted, not only set on the returned instance");
  }

  @Test
  void anExactMatchScoresAtOrNearOne() {
    Document indexed = index("Doc One", "Semantic vector search over documents.");

    var results = search("Doc One Semantic vector search over documents.", 5, 0.0);

    assertFalse(results.isEmpty());
    assertEquals(indexed.getId(), results.get(0).getKey());
    assertEquals(1.0, results.get(0).getValue(), 1e-6);
  }

  @Test
  void aPartialMatchScoresBetweenZeroAndOne() {
    index("Doc One", "Semantic vector search over documents.");

    double score = search("vector search", 5, 0.0).get(0).getValue();

    // A partial overlap must land strictly between "no match" and "identical";
    // an embedder without graded similarity collapses this to 0.0 or 1.0.
    assertTrue(score > 0.05 && score < 1.0, "partial match scored " + score);
  }

  @Test
  void unrelatedTextDoesNotClearAHighThreshold() {
    index("Doc One", "Semantic vector search over documents.");

    assertTrue(search("sourdough bread proving basket", 5, 0.5).isEmpty());
  }

  @Test
  void nearestNeighbourOrdersByDescendingSimilarity() {
    index("Vectors", "Dense vector similarity for retrieval.");
    index("Cooking", "Slow roasted vegetables with herbs.");

    var results = search("dense vector retrieval", 5, 0.0);

    for (int i = 1; i < results.size(); i++) {
      assertTrue(results.get(i - 1).getValue() >= results.get(i).getValue());
    }
  }

  @Test
  void limitCapsTheNumberOfNeighbours() {
    index("One", "Ranking signals for retrieval.");
    index("Two", "Retrieval ranking heuristics.");
    index("Three", "More about retrieval ranking.");

    assertEquals(2, search("retrieval ranking", 2, 0.0).size());
  }

  @Test
  void deletingAVectorRemovesItFromResults() {
    Document indexed = index("Doc One", "Semantic vector search over documents.");

    assertTrue(indexService.deleteDocumentVectors(indexed));

    assertTrue(search("semantic vector search", 5, 0.0).isEmpty());
  }

  @Test
  void rebuildRepopulatesAnIndexThatLostItsVectors() {
    Document first = index("One", "Ranking signals for retrieval.");
    Document second = index("Two", "Retrieval ranking heuristics.");

    // Drop both vectors while leaving the rows alone, which is what a re-created
    // or lost index looks like from the database side.
    indexService.deleteDocumentVectors(first);
    indexService.deleteDocumentVectors(second);
    assertTrue(
        search("retrieval ranking", 5, 0.0).isEmpty(), "the index should be empty at this point");

    assertEquals(2, indexService.rebuildIndex());

    assertEquals(2, search("retrieval ranking", 5, 0.0).size());
    assertNotNull(documentRepository.findById(first.getId()).orElseThrow().getVectorId());
  }

  @Test
  void reIndexingADocumentUpsertsRatherThanAddingASecondVector() {
    Document indexed = index("One", "Ranking signals for retrieval.");

    assertEquals(
        indexed.getId().toString(),
        indexed.getVectorId(),
        "the vector id must be the document id, which is what makes an index write an upsert");

    indexService.indexDocument(indexed);
    indexService.indexDocument(indexed);

    assertEquals(
        1,
        search("ranking signals retrieval", 5, 0.0).size(),
        "re-indexing must overwrite the vector, not add another that matches independently");
  }

  private String hash(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return Base64.getEncoder()
          .encodeToString(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unable to hash content", e);
    }
  }
}
