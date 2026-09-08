package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;

/**
 * Exercises retrieval against a real Elasticsearch, which the rest of the suite never does because
 * {@code elasticsearch.stub-enabled} defaults to true.
 *
 * <p>It covers the parts of {@link IndexService} that only a real cluster can exercise: the kNN
 * query shape, the {@code dense_vector} mapping, the flattened metadata field and the score
 * conversion. The score assertions matter most. Elasticsearch reports a cosine kNN hit as {@code (1
 * + cosine) / 2}, so dropping the conversion would leave unrelated documents scoring 0.5 and change
 * what every {@code minScore} means.
 *
 * <p>Skipped when no Docker daemon is available so a local build without Docker still passes.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ElasticsearchKnnTest {

  private static final int DIMENSIONS = 128;

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH =
      new ElasticsearchContainer(
              DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.10.4"))
          // Single node with security off keeps the fixture to plain HTTP; this is a
          // throwaway container, not a deployment.
          .withEnv("xpack.security.enabled", "false")
          .withEnv("discovery.type", "single-node")
          .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

  @DynamicPropertySource
  static void elasticsearchProperties(DynamicPropertyRegistry registry) {
    registry.add("elasticsearch.stub-enabled", () -> "false");
    registry.add("elasticsearch.host", ELASTICSEARCH::getHost);
    registry.add("elasticsearch.port", () -> ELASTICSEARCH.getMappedPort(9200));
    registry.add("elasticsearch.protocol", () -> "http");
    registry.add("management.health.elasticsearch.enabled", () -> "true");
    registry.add("embedding.dimensions", () -> DIMENSIONS);
  }

  @Autowired private IndexService indexService;
  @Autowired private EmbeddingService embeddingService;
  @Autowired private DocumentRepository documentRepository;

  @BeforeEach
  void resetIndex() {
    documentRepository.deleteAll();
    indexService.rebuildIndex();
  }

  private Document index(String title, String content, Map<String, String> metadata) {
    Document document = new Document();
    document.setTitle(title);
    document.setContent(content);
    document.setMetadata(metadata);
    document.setContentHash(hash(title + content));
    Document indexed = indexService.indexDocument(documentRepository.save(document));
    indexService.refreshIndex();
    return indexed;
  }

  private List<Map.Entry<UUID, Double>> search(String query, int limit, double minScore) {
    return search(query, limit, minScore, Map.of());
  }

  private List<Map.Entry<UUID, Double>> search(
      String query, int limit, double minScore, Map<String, String> filters) {
    return indexService.findSimilarDocuments(
        embeddingService.embed(query), limit, minScore, filters);
  }

  /**
   * Hybrid retrieval unions two candidate lists, so a document the lexical side found needs its
   * similarity read back out of the index. Against Elasticsearch that is an mget and a cosine
   * computed here, a different code path from the kNN search above and one nothing else covers.
   */
  @Test
  void similarityToReadsStoredVectorsBackOnTheSameScaleAsKnn() {
    Document match = index("Vector Search", "Vector search compares embeddings.", Map.of());
    Document unrelated = index("Bread Baking", "Sourdough needs a long cold proof.", Map.of());

    List<Double> queryVector = embeddingService.embed("vector search embeddings");
    Map<UUID, Double> scores =
        indexService.similarityTo(queryVector, List.of(match.getId(), unrelated.getId()));

    assertEquals(2, scores.size(), "both indexed documents should have been found");
    assertTrue(
        scores.get(match.getId()) > scores.get(unrelated.getId()),
        "matching " + scores.get(match.getId()) + ", unrelated " + scores.get(unrelated.getId()));

    // The same number the kNN search reports for the same document. Without that
    // a document recovered by the lexical side would be scored on a different
    // scale from one kNN returned, and the two would not be comparable.
    double fromKnn = search("vector search embeddings", 5, 0.0).get(0).getValue();
    assertEquals(fromKnn, scores.get(match.getId()), 1e-6);
  }

  /** Ids the index does not hold are absent, so a caller can tell that apart from a zero score. */
  @Test
  void similarityToOmitsDocumentsTheIndexDoesNotHold() {
    Document indexed = index("Vector Search", "Vector search compares embeddings.", Map.of());
    UUID absent = UUID.randomUUID();

    Map<UUID, Double> scores =
        indexService.similarityTo(
            embeddingService.embed("vector search"), List.of(indexed.getId(), absent));

    assertEquals(Set.of(indexed.getId()), scores.keySet());
  }

  @Test
  void aLongDocumentIsStoredAsSeveralPassagesAndReturnedOnce() {
    Document indexed = index("Long", longText("trapped ion qubits hold entanglement"), Map.of());

    assertTrue(indexed.getPassageCount() > 1, "the fixture has to be long enough to split");
    List<Map.Entry<UUID, Double>> results = search("trapped ion qubits entanglement", 10, 0.0);

    // Several passages can match. The caller asked about documents.
    assertEquals(
        1,
        results.stream().filter(hit -> hit.getKey().equals(indexed.getId())).count(),
        "the same document came back more than once");
  }

  @Test
  void shorteningADocumentDeletesThePassagesItNoLongerHas() {
    Document indexed = index("Long", longText("trapped ion qubits hold entanglement"), Map.of());
    assertTrue(indexed.getPassageCount() > 1);

    indexed.setContent("Tomatoes and courgettes in August.");
    Document shortened = indexService.updateDocumentIndex(indexed);
    indexService.refreshIndex();

    assertEquals(1, shortened.getPassageCount());
    assertTrue(
        search("trapped ion qubits entanglement", 10, 0.6).isEmpty(),
        "a passage of the previous version is still in the index");
  }

  @Test
  void deletingADocumentRemovesEveryPassage() {
    Document indexed = index("Long", longText("trapped ion qubits hold entanglement"), Map.of());
    assertTrue(indexed.getPassageCount() > 1);

    assertTrue(indexService.deleteDocumentVectors(indexed));
    indexService.refreshIndex();

    assertTrue(search("trapped ion qubits entanglement", 10, 0.0).isEmpty());
    assertTrue(search("tomatoes courgettes stone fruit", 10, 0.0).isEmpty());
  }

  /** Four hundred words of filler with {@code tail} at the end, past any one embedding window. */
  private static String longText(String tail) {
    StringBuilder text = new StringBuilder();
    for (int week = 0; week < 20; week++) {
      text.append("Late summer brings a glut of tomatoes, courgettes and stone fruit, and the ")
          .append("kitchen plans week ")
          .append(week)
          .append(" of its menu around whatever the growers deliver. Preserving what cannot be ")
          .append("served fresh keeps the cost of the winter menu down. ");
    }
    return text.append(tail).toString();
  }

  @Test
  void theRealIndexAcceptsWritesAndReturnsThem() {
    Document indexed = index("Vector Search", "Vector search compares embeddings.", Map.of());

    assertTrue(indexed.isIndexed());
    assertEquals(indexed.getId().toString(), indexed.getVectorId());

    List<Map.Entry<UUID, Double>> results = search("vector search embeddings", 5, 0.0);

    assertFalse(results.isEmpty(), "a kNN search must return the indexed document");
    assertEquals(indexed.getId(), results.get(0).getKey());
  }

  /**
   * The conversion back to cosine is what this pins. Elasticsearch would report roughly 1.0 for an
   * identical vector either way, so the unrelated-document assertion is the one that fails if the
   * conversion is dropped: its raw kNN score sits near 0.5.
   */
  @Test
  void scoresAreReportedOnTheCosineScaleNotElasticsearchsShiftedScale() {
    String text = "Vector search compares embeddings.";
    Document indexed = index("Vector Search", text, Map.of());
    index("Baking", "Sourdough proving baskets and oven temperatures.", Map.of());

    double exactMatch = scoreOf(search("Vector Search " + text, 10, 0.0), indexed.getId());
    assertEquals(1.0, exactMatch, 1e-3, "a document matched against itself must score 1.0");

    double unrelated =
        search("sourdough proving baskets", 10, 0.0).stream()
            .filter(entry -> entry.getKey().equals(indexed.getId()))
            .mapToDouble(Map.Entry::getValue)
            .findFirst()
            .orElse(0.0);
    assertTrue(
        unrelated < 0.5,
        "an unrelated document must score below 0.5 on the cosine scale, got " + unrelated);
  }

  @Test
  void aThresholdOnTheCosineScaleExcludesUnrelatedDocuments() {
    index("Vector Search", "Vector search compares embeddings.", Map.of());

    assertTrue(
        search("sourdough bread proving basket", 5, 0.5).isEmpty(),
        "a 0.5 cosine threshold must reject an unrelated document");
    assertFalse(
        search("vector search compares embeddings", 5, 0.5).isEmpty(),
        "the same threshold must still admit a close match");
  }

  @Test
  void metadataFiltersAreAppliedInsideTheKnnSearch() {
    Document ranking =
        index(
            "Ranking Signals", "Ranking blends similarity and boosts.", Map.of("topic", "ranking"));
    index("Latency", "Latency budgets bound search responses.", Map.of("topic", "performance"));

    List<Map.Entry<UUID, Double>> filtered =
        search("ranking latency search", 10, 0.0, Map.of("topic", "ranking"));

    assertEquals(1, filtered.size(), "only the matching topic should be retrieved");
    assertEquals(ranking.getId(), filtered.get(0).getKey());
  }

  @Test
  void metadataFilterMatchingIgnoresCase() {
    Document ranking =
        index(
            "Ranking Signals", "Ranking blends similarity and boosts.", Map.of("topic", "Ranking"));

    List<Map.Entry<UUID, Double>> filtered =
        search("ranking signals", 10, 0.0, Map.of("topic", "rAnKiNg"));

    assertEquals(1, filtered.size(), "filters are documented as case-insensitive");
    assertEquals(ranking.getId(), filtered.get(0).getKey());
  }

  @Test
  void reIndexingOverwritesTheVectorRatherThanAddingASecond() {
    Document indexed = index("Ranking", "Ranking blends similarity and boosts.", Map.of());

    indexService.indexDocument(indexed);
    indexService.indexDocument(indexed);
    indexService.refreshIndex();

    assertEquals(
        1,
        search("ranking similarity boosts", 10, 0.0).size(),
        "the document id is the vector id, so re-indexing must upsert");
  }

  @Test
  void deletingAVectorRemovesItFromRetrieval() {
    Document indexed = index("Ranking", "Ranking blends similarity and boosts.", Map.of());

    assertTrue(indexService.deleteDocumentVectors(indexed));
    indexService.refreshIndex();

    assertTrue(search("ranking similarity boosts", 5, 0.0).isEmpty());
  }

  private static double scoreOf(List<Map.Entry<UUID, Double>> results, UUID id) {
    return results.stream()
        .filter(entry -> entry.getKey().equals(id))
        .mapToDouble(Map.Entry::getValue)
        .findFirst()
        .orElseThrow(() -> new AssertionError("document " + id + " was not retrieved"));
  }

  private static String hash(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return Base64.getEncoder()
          .encodeToString(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unable to hash content", e);
    }
  }
}
