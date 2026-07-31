package io.github.semanticsearch.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.semanticsearch.repository.DocumentRepository;
import io.github.semanticsearch.service.IndexService;

/**
 * Search responses are cached, so every write has to invalidate them.
 *
 * <p>Each test issues the same query before and after a mutation. A cache that is never evicted
 * answers the second call from the first call's entry, so the corpus change is invisible and the
 * count assertion fails. {@link #theSearchCacheIsActuallyPopulated()} guards the rest: if caching
 * were switched off for tests these assertions would all pass while proving nothing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchCacheInvalidationTest {

  private static final String QUERY = "ranking signals and boosts";

  @Autowired private MockMvc mockMvc;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private IndexService indexService;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void reset() {
    documentRepository.deleteAll();
    indexService.rebuildIndex();
  }

  private String create(String title, String content) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/v1/documents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title":"%s","content":"%s","metadata":{"topic":"ranking"}}
                        """
                            .formatted(title, content)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(response).get("id").asText();
  }

  private JsonNode results() throws Exception {
    String body =
        mockMvc
            .perform(get("/api/v1/search").param("query", QUERY).param("minScore", "0.0"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body);
  }

  private int resultCount() throws Exception {
    return results().size();
  }

  private List<String> resultTitles() throws Exception {
    List<String> titles = new ArrayList<>();
    results().forEach(node -> titles.add(node.get("title").asText()));
    return titles;
  }

  @Test
  void theSearchCacheIsActuallyPopulated() throws Exception {
    create("Ranking Signals", "Ranking blends semantic similarity with metadata boosts.");
    Cache cache = cacheManager.getCache("searchResults");
    assertNotNull(
        cache, "the searchResults cache must exist for the other tests here to mean anything");

    assertEquals(1, resultCount());

    assertTrue(
        ((ConcurrentMapCache) cache).getNativeCache().size() > 0,
        "a search must leave a cache entry behind, otherwise eviction is untested");
  }

  @Test
  void aNewDocumentAppearsInAQueryThatWasAlreadyCached() throws Exception {
    create("Ranking Signals", "Ranking blends semantic similarity with metadata boosts.");
    assertEquals(1, resultCount());

    create("More On Ranking", "Further notes on ranking signals and how boosts are applied.");

    assertEquals(2, resultCount(), "a created document must be visible to an already-cached query");
  }

  @Test
  void aDeletedDocumentDisappearsFromAQueryThatWasAlreadyCached() throws Exception {
    create("Ranking Signals", "Ranking blends semantic similarity with metadata boosts.");
    String doomed = create("More On Ranking", "Further notes on ranking signals and boosts.");
    assertEquals(2, resultCount());

    mockMvc.perform(delete("/api/v1/documents/{id}", doomed)).andExpect(status().isNoContent());

    assertEquals(1, resultCount(), "a deleted document must not be served from cache");
  }

  @Test
  void anEditedDocumentIsReturnedWithItsNewContentNotTheCachedCopy() throws Exception {
    String id = create("Sourdough Baking", "Proving baskets and oven temperatures for bread.");
    assertTrue(resultTitles().contains("Sourdough Baking"), "the document should be retrievable");

    mockMvc
        .perform(
            put("/api/v1/documents/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"Ranking Signals","content":"Ranking signals and boosts decide order."}
                    """))
        .andExpect(status().isOk());

    // The cached response body carries the old title, so serving it here would
    // report a document that no longer exists in that form.
    assertTrue(
        resultTitles().contains("Ranking Signals"),
        "an edited document must be returned with its new title, got " + resultTitles());
    assertFalse(
        resultTitles().contains("Sourdough Baking"),
        "the pre-edit title must not survive in cache");
  }

  @Test
  void aRebuildInvalidatesCachedResults() throws Exception {
    create("Ranking Signals", "Ranking blends semantic similarity with metadata boosts.");
    assertEquals(1, resultCount());

    documentRepository.deleteAll();
    indexService.rebuildIndex();

    assertEquals(
        0, resultCount(), "a rebuilt index must not answer from the previous index's cache");
  }
}
