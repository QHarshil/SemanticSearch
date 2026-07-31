package io.github.semanticsearch.controller;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;
import io.github.semanticsearch.service.IndexService;

/**
 * Exercises the search API over HTTP against the real stack: in-memory vector index, local embedder
 * and H2.
 *
 * <p>Driven over HTTP rather than by calling controller methods directly, because the request
 * mapping, the parameter names, the bean validation annotations and the JSON field names are part
 * of the contract clients depend on. Invoking the methods as plain Java would leave all of those
 * unverified, so renaming {@code query} to {@code q} or moving a route would still pass.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private IndexService indexService;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void seedCorpus() {
    documentRepository.deleteAll();
    // The in-memory vector index is a singleton across the test context, and a
    // repository-level deleteAll leaves its vectors behind pointing at rows that
    // no longer exist. Rebuilding clears them.
    indexService.rebuildIndex();
    index("Vector Search Basics", "Vector search finds similar documents by comparing embeddings.");
    index("Ranking Signals", "Ranking blends semantic similarity with metadata boosts.");
    index("Latency Budgets", "Latency budgets keep search responses under a target p95.");
  }

  private void index(String title, String content) {
    Document document = new Document();
    document.setTitle(title);
    document.setContent(content);
    document.setContentHash(Integer.toHexString(content.hashCode()));
    document.setMetadata(Map.of("topic", title.toLowerCase().split(" ")[0]));
    indexService.indexDocument(documentRepository.save(document));
  }

  @Test
  void getSearchReturnsRankedResultsWithTheDocumentedFieldNames() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/search")
                .param("query", "vector search embeddings")
                .param("minScore", "0.0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title", is("Vector Search Basics")))
        .andExpect(jsonPath("$[0].id").exists())
        .andExpect(jsonPath("$[0].content").exists())
        .andExpect(jsonPath("$[0].score", is(greaterThan(0.0))))
        .andExpect(jsonPath("$[0].metadata.topic").exists());
  }

  @Test
  void rankingPutsTheBestLexicalMatchFirst() throws Exception {
    // The query shares no exact phrase with any document, so this only passes if
    // similarity is graded. An embedder without input locality scores every one of
    // these at zero and returns nothing.
    mockMvc
        .perform(
            get("/api/v1/search").param("query", "latency p95 budget").param("minScore", "0.0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title", is("Latency Budgets")));
  }

  @Test
  void resultsAreOrderedByDescendingScore() throws Exception {
    String body =
        mockMvc
            .perform(
                get("/api/v1/search").param("query", "ranking metadata").param("minScore", "0.0"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    var results = objectMapper.readTree(body);
    for (int i = 1; i < results.size(); i++) {
      double previous = results.get(i - 1).get("score").asDouble();
      double current = results.get(i).get("score").asDouble();
      // Guards the re-sort after hybrid blending, boosts and recency. Without it
      // the array order reflects raw vector score while the score field does not.
      org.junit.jupiter.api.Assertions.assertTrue(
          previous >= current,
          "results must be ordered by the score they report, but "
              + previous
              + " preceded "
              + current);
    }
  }

  /**
   * minScore is documented as a floor on the score in the response, so a threshold set just below a
   * score the API itself reported must still return that document.
   *
   * <p>The threshold is derived from the reported score rather than hard-coded, which is what makes
   * this falsifiable: applying the floor to the raw vector score instead is strictly harsher,
   * because blending a strong lexical match lifts a candidate above the vector score it came in
   * with.
   */
  @Test
  void minScoreFiltersTheScoreTheResponseReports() throws Exception {
    var unfiltered = searchResults("latency p95 budget", "0.0");
    double topScore = unfiltered.get(0).get("score").asDouble();
    String topTitle = unfiltered.get(0).get("title").asText();
    String justBelowTop = String.format(java.util.Locale.ROOT, "%.6f", topScore - 1e-4);

    var filtered = searchResults("latency p95 budget", justBelowTop);

    org.junit.jupiter.api.Assertions.assertFalse(
        filtered.isEmpty(),
        "a floor of "
            + justBelowTop
            + " must still return the document the API scored "
            + topScore);
    org.junit.jupiter.api.Assertions.assertEquals(topTitle, filtered.get(0).get("title").asText());
    for (var result : filtered) {
      double score = result.get("score").asDouble();
      org.junit.jupiter.api.Assertions.assertTrue(
          score >= topScore - 1e-4, "returned a result scoring " + score + " below the floor");
    }
  }

  private com.fasterxml.jackson.databind.JsonNode searchResults(String query, String minScore)
      throws Exception {
    String body =
        mockMvc
            .perform(get("/api/v1/search").param("query", query).param("minScore", minScore))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body);
  }

  @Test
  void theQueryParameterIsNamedQuery() throws Exception {
    // The parameter is "query"; "q" must be rejected rather than quietly ignored,
    // which is what pins the name documented in the README and OpenAPI schema.
    mockMvc.perform(get("/api/v1/search").param("q", "vector")).andExpect(status().isBadRequest());
  }

  @Test
  void blankQueryIsRejected() throws Exception {
    mockMvc.perform(get("/api/v1/search").param("query", "   ")).andExpect(status().isBadRequest());
  }

  @Test
  void nonPositiveLimitIsRejected() throws Exception {
    mockMvc
        .perform(get("/api/v1/search").param("query", "vector").param("limit", "0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void minScoreAboveOneIsRejected() throws Exception {
    mockMvc
        .perform(get("/api/v1/search").param("query", "vector").param("minScore", "1.5"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void limitCapsTheNumberOfResults() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/search")
                .param("query", "search")
                .param("minScore", "0.0")
                .param("limit", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  @Test
  void includeContentFalseOmitsDocumentBodies() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/search")
                .param("query", "vector search")
                .param("minScore", "0.0")
                .param("includeContent", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].content").doesNotExist());
  }

  @Test
  void advancedSearchAcceptsAJsonBodyAndAppliesFilters() throws Exception {
    String body =
        """
        {"query":"vector search","minScore":0.0,"limit":5,"filters":{"topic":"vector"}}
        """;

    mockMvc
        .perform(
            post("/api/v1/search/advanced").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].title", is("Vector Search Basics")));
  }

  @Test
  void advancedSearchHasNoMaxResultsField() throws Exception {
    // The field is "limit". A plausible-looking alternative like "maxResults"
    // must not appear to work: an ignored unknown property would cap nothing and
    // still answer 200, so assert the request is rejected.
    String body =
        """
        {"query":"search","minScore":0.0,"maxResults":1}
        """;

    mockMvc
        .perform(
            post("/api/v1/search/advanced").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }
}
