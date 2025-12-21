package io.github.semanticsearch.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.github.semanticsearch.model.SearchRequest;
import io.github.semanticsearch.model.SearchResult;
import io.github.semanticsearch.service.IndexService;
import io.github.semanticsearch.service.SearchService;

class SearchControllerTest {

  private StubSearchService searchService;
  private RecordingIndexService indexService;
  private SearchController controller;
  private UUID documentId;
  private SearchResult testResult;

  @BeforeEach
  void setUp() {
    documentId = UUID.randomUUID();
    testResult =
        SearchResult.builder()
            .id(documentId)
            .title("Test Document")
            .content("This is test content")
            .score(0.85)
            .metadata(Map.of("key", "value"))
            .highlights(List.of("This is test content"))
            .build();

    searchService = new StubSearchService(List.of(testResult));
    indexService = new RecordingIndexService();
    controller = new SearchController(searchService, indexService);
  }

  @Test
  void search_buildsRequestAndReturnsResults() {
    ResponseEntity<List<SearchResult>> response = controller.search("test", 10, 0.7, true, true);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(1, response.getBody().size());
    assertEquals("test", searchService.lastRequest.getQuery());
    assertEquals(10, searchService.lastRequest.getLimit());
    assertTrue(searchService.lastRequest.isIncludeContent());
  }

  @Test
  void advancedSearch_usesBodyRequest() {
    SearchRequest request =
        SearchRequest.builder()
            .query("advanced")
            .limit(5)
            .minScore(0.4)
            .filters(Map.of("category", "article"))
            .fields(List.of("title"))
            .includeContent(false)
            .includeHighlights(false)
            .build();

    ResponseEntity<List<SearchResult>> response = controller.advancedSearch(request);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(request, searchService.lastRequest);
    assertEquals(1, response.getBody().size());
  }

  @Test
  void findSimilar_delegatesToService() {
    ResponseEntity<List<SearchResult>> response = controller.findSimilar(documentId, 3, 0.6);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(documentId, searchService.lastSimilarId);
    assertEquals(3, searchService.lastLimit);
    assertEquals(0.6, searchService.lastMinScore);
  }

  @Test
  void rebuildIndex_callsInitializer() {
    ResponseEntity<String> response = controller.rebuildIndex();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertTrue(indexService.initializeCalled);
  }

  private static class StubSearchService extends SearchService {
    SearchRequest lastRequest;
    UUID lastSimilarId;
    int lastLimit;
    double lastMinScore;
    private final List<SearchResult> cannedResults;

    StubSearchService(List<SearchResult> cannedResults) {
      super(null, null, null, new io.github.semanticsearch.config.SearchProperties());
      this.cannedResults = cannedResults;
    }

    @Override
    public List<SearchResult> search(SearchRequest request) {
      this.lastRequest = request;
      return cannedResults;
    }

    @Override
    public List<SearchResult> findSimilarDocuments(UUID documentId, int limit, double minScore) {
      this.lastSimilarId = documentId;
      this.lastLimit = limit;
      this.lastMinScore = minScore;
      return cannedResults;
    }
  }

  private static class RecordingIndexService extends IndexService {
    boolean initializeCalled = false;

    RecordingIndexService() {
      super(null, null, null);
    }

    @Override
    public void initializeIndex() {
      initializeCalled = true;
    }
  }
}
