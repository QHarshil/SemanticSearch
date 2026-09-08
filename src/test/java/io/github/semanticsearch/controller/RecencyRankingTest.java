package io.github.semanticsearch.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;
import io.github.semanticsearch.service.IndexService;

/**
 * Covers what recency decay does to the results the API returns.
 *
 * <p>The rest of the suite runs with {@code search.recency-enabled=false}, because a score that
 * moves with wall-clock time makes ranking assertions elsewhere ambiguous. That leaves this the
 * only place the decay curve meets a real corpus, a real {@code minScore} and the HTTP contract, so
 * both halves of the behaviour are pinned here: age has to reorder results, and it has to stop
 * short of removing them.
 */
@SpringBootTest(
    properties = {
      "search.recency-enabled=true",
      "search.recency-half-life-seconds=604800",
      "search.recency-floor=0.7"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecencyRankingTest {

  private static final Duration ONE_YEAR = Duration.ofDays(365);

  /** The documented default applied when a request omits {@code minScore}. */
  private static final double DEFAULT_MIN_SCORE = 0.2;

  @Autowired private MockMvc mockMvc;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private IndexService indexService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private CacheManager cacheManager;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void resetCorpus() {
    documentRepository.deleteAll();
    indexService.rebuildIndex();
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
  }

  @Test
  void aYearOldDocumentStillClearsTheDefaultMinScore() throws Exception {
    UUID id = index("Latency Budgets", "Latency budgets keep search responses under a target p95.");
    age(id, ONE_YEAR);

    // The request omits minScore, so the default applies. A year is over fifty
    // half-lives: an unbounded exponential leaves this document scoring around
    // 1e-16 and search answers with an empty array however well it matches.
    JsonNode results = search("latency p95 budget");

    assertEquals(1, results.size(), "aged document was filtered out of the results");
    assertEquals("Latency Budgets", results.get(0).get("title").asText());
    assertTrue(
        results.get(0).get("score").asDouble() > DEFAULT_MIN_SCORE,
        "scored " + results.get(0).get("score").asDouble() + " at one year old");
  }

  @Test
  void theFresherOfTwoEquallyRelevantDocumentsRanksFirst() throws Exception {
    String content = "Latency budgets keep search responses under a target p95.";
    index("Latency Budgets Alpha", content);
    UUID beta = index("Latency Budgets Beta", content);
    age(beta, ONE_YEAR);

    // Identical content and symmetric titles, so the two differ only in age.
    JsonNode results = search("latency p95 budget");

    assertEquals(2, results.size(), "aged document was filtered out of the results");
    assertEquals("Latency Budgets Alpha", results.get(0).get("title").asText());
    assertEquals("Latency Budgets Beta", results.get(1).get("title").asText());
    assertTrue(
        results.get(0).get("score").asDouble() > results.get(1).get("score").asDouble(),
        "age did not separate the two scores");
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

  private UUID index(String title, String content) {
    Document document = new Document();
    document.setTitle(title);
    document.setContent(content);
    document.setContentHash(UUID.randomUUID().toString());
    document.setMetadata(Map.of("topic", "performance"));
    Document saved = documentRepository.save(document);
    indexService.indexDocument(saved);
    return saved.getId();
  }

  /**
   * Backdates a row's timestamps in SQL. {@code @LastModifiedDate} stamps {@code updated_at} on
   * every save, so a document cannot be aged through the repository.
   */
  private void age(UUID id, Duration age) {
    Instant when = Instant.now().minus(age);
    jdbcTemplate.update(
        "UPDATE documents SET created_at = ?, updated_at = ? WHERE id = ?", when, when, id);
  }
}
