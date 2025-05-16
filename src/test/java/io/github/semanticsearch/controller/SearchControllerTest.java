package io.github.semanticsearch.controller;

import io.github.semanticsearch.model.SearchRequest;
import io.github.semanticsearch.model.SearchResult;
import io.github.semanticsearch.service.IndexService;
import io.github.semanticsearch.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@WebFluxTest(SearchController.class)
public class SearchControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private SearchService searchService;

    @MockBean
    private IndexService indexService;

    private SearchResult testResult;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        documentId = UUID.randomUUID();
        
        testResult = SearchResult.builder()
                .id(documentId)
                .title("Test Document")
                .content("This is test content")
                .score(0.85f)
                .metadata(Map.of("key", "value"))
                .highlights(List.of("This is test content"))
                .build();
    }

    @Test
    void testSearch_Success() {
        // Arrange
        when(searchService.search(any(SearchRequest.class))).thenReturn(List.of(testResult));
        
        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/search?query=test&limit=10&minScore=0.7&includeContent=true&includeHighlights=true")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(SearchResult.class)
                .hasSize(1)
                .contains(testResult);
        
        verify(searchService).search(any(SearchRequest.class));
    }

    @Test
    void testAdvancedSearch_Success() {
        // Arrange
        SearchRequest request = SearchRequest.builder()
                .query("test query")
                .limit(10)
                .minScore(0.7f)
                .filters(Map.of("category", "article"))
                .fields(List.of("title", "content"))
                .includeContent(true)
                .includeHighlights(true)
                .build();
        
        when(searchService.search(any(SearchRequest.class))).thenReturn(List.of(testResult));
        
        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/search/advanced")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(SearchResult.class)
                .hasSize(1)
                .contains(testResult);
        
        verify(searchService).search(any(SearchRequest.class));
    }

    @Test
    void testFindSimilar_Success() {
        // Arrange
        when(searchService.findSimilarDocuments(eq(documentId), anyInt(), anyFloat()))
                .thenReturn(List.of(testResult));
        
        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/search/similar/{id}?limit=10&minScore=0.7", documentId)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(SearchResult.class)
                .hasSize(1)
                .contains(testResult);
        
        verify(searchService).findSimilarDocuments(eq(documentId), eq(10), eq(0.7f));
    }

    @Test
    void testRebuildIndex_Success() {
        // Arrange
        doNothing().when(indexService).initializeIndex();
        
        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/search/index/rebuild")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Index rebuilt successfully");
        
        verify(indexService).initializeIndex();
    }
}
