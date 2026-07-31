package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.model.SearchRequest;
import io.github.semanticsearch.model.SearchResult;
import io.github.semanticsearch.repository.DocumentRepository;

/**
 * Behaviour of the search pipeline itself: ranking, filtering and projection.
 *
 * <p>Queries are ordinary phrases that overlap the documents partially, not document content passed
 * back verbatim. Searching for text identical to a stored document exercises the one case where
 * search is unnecessary, and passes for any embedder that can recognise an exact copy.
 */
@SpringBootTest
@ActiveProfiles("test")
class SearchServiceTest {

  @Autowired private SearchService searchService;
  @Autowired private IndexService indexService;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void resetCorpus() {
    documentRepository.deleteAll();
    indexService.rebuildIndex();
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
  }

  private Document index(String title, String content, Map<String, String> metadata) {
    Document document = new Document();
    document.setTitle(title);
    document.setContent(content);
    document.setMetadata(metadata);
    document.setContentHash(hash(content));
    return indexService.indexDocument(documentRepository.save(document));
  }

  private SearchRequest.Builder query(String text) {
    return SearchRequest.builder().query(text).limit(10).minScore(0.0);
  }

  @Test
  void ranksTheMoreRelevantDocumentFirst() {
    index("Vector Retrieval", "Nearest neighbour lookup over dense embedding vectors.", Map.of());
    index("Baking Sourdough", "Feeding a starter and shaping the loaf before proving.", Map.of());

    List<SearchResult> results = searchService.search(query("dense vector lookup").build());

    assertFalse(results.isEmpty());
    assertEquals("Vector Retrieval", results.get(0).getTitle());
  }

  @Test
  void returnsResultsSortedByTheScoreTheyReport() {
    index("Alpha Ranking", "Ranking documents by relevance signals.", Map.of());
    index("Beta Ranking", "Ranking and ordering of retrieved documents.", Map.of());
    index("Unrelated", "A recipe for tomato soup.", Map.of());

    List<SearchResult> results = searchService.search(query("ranking documents").build());

    for (int i = 1; i < results.size(); i++) {
      assertTrue(
          results.get(i - 1).getScore() >= results.get(i).getScore(),
          "results must be ordered by their own score field");
    }
  }

  @Test
  void metadataFiltersExcludeNonMatchingDocuments() {
    index("Public Note", "Shared guidance about ranking.", Map.of("visibility", "public"));
    index("Private Note", "Internal guidance about ranking.", Map.of("visibility", "private"));

    List<SearchResult> results =
        searchService.search(
            query("guidance ranking").filters(Map.of("visibility", "public")).build());

    assertEquals(1, results.size());
    assertEquals("Public Note", results.get(0).getTitle());
  }

  @Test
  void fieldsProjectionLimitsReturnedMetadataKeys() {
    index(
        "Tagged",
        "A document carrying several metadata keys about ranking.",
        Map.of("domain", "search", "owner", "platform", "tier", "gold"));

    SearchResult result =
        searchService.search(query("ranking metadata").fields(List.of("domain")).build()).get(0);

    assertEquals(Map.of("domain", "search"), result.getMetadata());
  }

  @Test
  void highlightsAndContentCanBeSuppressed() {
    index("Highlighted", "Vector similarity underpins semantic ranking.", Map.of());

    SearchResult withExtras = searchService.search(query("vector similarity").build()).get(0);
    assertNotNull(withExtras.getContent());
    assertNotNull(withExtras.getHighlights());
    assertFalse(withExtras.getHighlights().isEmpty());

    SearchResult withoutExtras =
        searchService
            .search(
                query("vector similarity").includeContent(false).includeHighlights(false).build())
            .get(0);
    assertNull(withoutExtras.getContent());
    assertNull(withoutExtras.getHighlights());
  }

  @Test
  void limitCapsResultsAfterReRanking() {
    index("One", "Ranking signals for retrieval.", Map.of());
    index("Two", "Retrieval ranking heuristics.", Map.of());
    index("Three", "Signals used when ranking retrieval output.", Map.of());

    List<SearchResult> results = searchService.search(query("ranking retrieval").limit(2).build());

    assertEquals(2, results.size());
  }

  @Test
  void minScoreExcludesWeakMatches() {
    index("Vector Retrieval", "Nearest neighbour lookup over dense embedding vectors.", Map.of());
    index("Baking Sourdough", "Feeding a starter and shaping the loaf before proving.", Map.of());

    // A threshold this high can only be met by a strong match, so the unrelated
    // document must not appear. This also pins that scores live in [0,1].
    List<SearchResult> results =
        searchService.search(query("dense vector lookup").minScore(0.9).build());

    assertTrue(
        results.stream().noneMatch(r -> r.getTitle().equals("Baking Sourdough")),
        "an unrelated document cleared a 0.9 similarity threshold");
    assertTrue(results.stream().allMatch(r -> r.getScore() >= 0.0 && r.getScore() <= 1.0));
  }

  @Test
  void returnsNothingWhenTheCorpusIsEmpty() {
    assertTrue(searchService.search(query("anything at all").build()).isEmpty());
  }

  private String hash(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return Base64.getEncoder().encodeToString(digest.digest(content.getBytes()));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unable to hash content", e);
    }
  }
}
