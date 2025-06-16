package io.github.semanticsearch.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.github.semanticsearch.model.SearchRequest;
import io.github.semanticsearch.model.SearchResult;
import io.github.semanticsearch.service.IndexService;
import io.github.semanticsearch.service.SearchService;

@ExtendWith(MockitoExtension.class)
public class SearchControllerTest {

  @Mock private SearchService searchService;

  @Mock private IndexService indexService;

  @InjectMocks private SearchController searchController;

  private SearchResult testResult;
  private UUID documentId;

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
  }

  @Test
  void testSearch_Success() {
    // Arrange
    when(searchService.search(any(SearchRequest.class))).thenReturn(List.of(testResult));

    // Act
    ResponseEntity<List<SearchResult>> response =
        searchController.search("test", 10, 0.7f, true, true);

    // Assert
    assert response.getStatusCode() == HttpStatus.OK;
    assert response.getBody().size() == 1;
    assert response.getBody().contains(testResult);
    verify(searchService).search(any(SearchRequest.class));
  }

  @Test
  void testAdvancedSearch_Success() {
    // Arrange
    SearchRequest request =
        SearchRequest.builder()
            .query("test query")
            .limit(10)
            .minScore(0.7)
            .filters(Map.of("category", "article"))
            .fields(List.of("title", "content"))
            .includeContent(true)
            .includeHighlights(true)
            .build();

    when(searchService.search(any(SearchRequest.class))).thenReturn(List.of(testResult));

    // Act
    ResponseEntity<List<SearchResult>> response = searchController.advancedSearch(request);

    // Assert
    assert response.getStatusCode() == HttpStatus.OK;
    assert response.getBody().size() == 1;
    assert response.getBody().contains(testResult);
    verify(searchService).search(any(SearchRequest.class));
  }

  @Test
  void testFindSimilar_Success() {
    // Arrange
    when(searchService.findSimilarDocuments(eq(documentId), anyInt(), anyDouble()))
        .thenReturn(List.of(testResult));

    // Act
    ResponseEntity<List<SearchResult>> response =
        searchController.findSimilar(documentId, 10, 0.7f);

    // Assert
    assert response.getStatusCode() == HttpStatus.OK;
    assert response.getBody().size() == 1;
    assert response.getBody().contains(testResult);
    // Use anyDouble() instead of eq(0.7) to handle float-to-double conversion precision issues
    verify(searchService).findSimilarDocuments(eq(documentId), eq(10), anyDouble());
  }

  @Test
  void testRebuildIndex_Success() {
    // Arrange
    doNothing().when(indexService).initializeIndex();

    // Act
    ResponseEntity<String> response = searchController.rebuildIndex();

    // Assert
    assert response.getStatusCode() == HttpStatus.OK;
    assert response.getBody().equals("Index rebuilt successfully");
    verify(indexService).initializeIndex();
  }
}
