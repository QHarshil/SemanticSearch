package io.github.semanticsearch.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import io.github.semanticsearch.repository.DocumentRepository;
import io.github.semanticsearch.service.IndexService;

/**
 * The eval endpoint over HTTP, including the cutoff the README tells people to pass.
 *
 * <p>{@code k} decides what NDCG and Recall mean, so a value the endpoint accepts and ignores would
 * report one measurement under another measurement's name.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EvalApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private IndexService indexService;
  @Autowired private CacheManager cacheManager;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void resetCorpus() {
    documentRepository.deleteAll();
    indexService.rebuildIndex();
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
  }

  @Test
  void returnsTheDocumentedFieldsAndSeedsTheCorpusItScores() throws Exception {
    JsonNode report = run("");

    assertEquals(8, report.get("totalQueries").asInt());
    assertEquals(8, report.get("details").size());
    assertTrue(report.has("mrr") && report.has("ndcg") && report.has("recallAtK"));
    assertTrue(report.get("details").get(0).has("rr"));
  }

  @Test
  void theCutoffChangesTheMeasurement() throws Exception {
    // A gold document at rank two counts at k=2 and not at k=1, so these two have
    // to disagree. Equal numbers would mean the parameter is being ignored.
    double atOne = run("?k=1").get("recallAtK").asDouble();
    double atFive = run("?k=5").get("recallAtK").asDouble();

    assertTrue(atOne < atFive, "recall was " + atOne + " at k=1 and " + atFive + " at k=5");
  }

  @Test
  void theDefaultCutoffIsFive() throws Exception {
    assertEquals(run("?k=5").get("ndcg").asDouble(), run("").get("ndcg").asDouble(), 1e-9);
  }

  @Test
  void aCutoffOfZeroIsRejected() throws Exception {
    // Every metric is defined over the first k results, and there is no honest
    // answer for none of them.
    mockMvc.perform(get("/api/v1/eval/run").param("k", "0")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/v1/eval/run").param("k", "-3")).andExpect(status().isBadRequest());
  }

  private JsonNode run(String query) throws Exception {
    String body =
        mockMvc
            .perform(get("/api/v1/eval/run" + query))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body);
  }
}
