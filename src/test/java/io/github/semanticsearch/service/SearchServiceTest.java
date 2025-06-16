package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.model.SearchRequest;
import io.github.semanticsearch.model.SearchResult;
import io.github.semanticsearch.repository.DocumentRepository;

@ExtendWith(MockitoExtension.class)
public class SearchServiceTest {

  @Mock private EmbeddingService embeddingService;

  @Mock private IndexService indexService;

  @Mock private DocumentRepository documentRepository;

  @InjectMocks private SearchService searchService;

  private Document testDocument;
  private UUID documentId;
  private List<Double> testVector;

  @BeforeEach
  void setUp() {
    documentId = UUID.randomUUID();
    testVector = Arrays.asList(0.1, 0.2, 0.3);

    testDocument = new Document();
    testDocument.setId(documentId);
    testDocument.setTitle("Test Document");
    testDocument.setContent("This is a test document content");
    testDocument.setContentHash("test-hash");
    testDocument.setMetadata(Map.of("key", "value"));
  }

  @Test
  void testSearch_Success() {
    // Arrange
    SearchRequest request =
        SearchRequest.builder()
            .query("test query")
            .limit(10)
            .minScore(0.7)
            .includeContent(true)
            .includeHighlights(true)
            .build();

    when(embeddingService.embed(anyString())).thenReturn(testVector);

    List<Map.Entry<UUID, Double>> similarDocuments = new ArrayList<>();
    similarDocuments.add(new AbstractMap.SimpleEntry<>(documentId, 0.85));
    when(indexService.findSimilarDocuments(eq(testVector), anyInt(), anyDouble()))
        .thenReturn(similarDocuments);

    when(documentRepository.findAllById(anyList()))
        .thenReturn(Collections.singletonList(testDocument));

    // Act
    List<SearchResult> results = searchService.search(request);

    // Assert
    assertFalse(results.isEmpty());
    assertEquals(1, results.size());
    assertEquals(documentId, results.get(0).getId());
    assertEquals("Test Document", results.get(0).getTitle());
    assertEquals("This is a test document content", results.get(0).getContent());
    assertEquals(0.85, results.get(0).getScore(), 0.001);

    verify(embeddingService).embed(request.getQuery());
    verify(indexService).findSimilarDocuments(eq(testVector), eq(10), eq(0.7));
    verify(documentRepository).findAllById(anyList());
  }

  @Test
  void testSearch_EmptyEmbedding() {
    // Arrange
    SearchRequest request =
        SearchRequest.builder().query("test query").limit(10).minScore(0.7).build();

    when(embeddingService.embed(anyString())).thenReturn(Collections.emptyList());

    // Act
    List<SearchResult> results = searchService.search(request);

    // Assert
    assertTrue(results.isEmpty());
    verify(embeddingService).embed(request.getQuery());
    verify(indexService, never()).findSimilarDocuments(any(), anyInt(), anyDouble());
    verify(documentRepository, never()).findAllById(anyList());
  }

  @Test
  void testSearch_NoSimilarDocuments() {
    // Arrange
    SearchRequest request =
        SearchRequest.builder().query("test query").limit(10).minScore(0.7).build();

    when(embeddingService.embed(anyString())).thenReturn(testVector);
    when(indexService.findSimilarDocuments(any(), anyInt(), anyDouble()))
        .thenReturn(Collections.emptyList());

    // Act
    List<SearchResult> results = searchService.search(request);

    // Assert
    assertTrue(results.isEmpty());
    verify(embeddingService).embed(request.getQuery());
    verify(indexService).findSimilarDocuments(eq(testVector), eq(10), eq(0.7));
    verify(documentRepository, never()).findAllById(anyList());
  }

  @Test
  void testFindSimilarDocuments_Success() {
    // Arrange
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(testDocument));
    when(embeddingService.embed(anyString())).thenReturn(testVector);

    UUID similarDocId = UUID.randomUUID();
    Document similarDoc = new Document();
    similarDoc.setId(similarDocId);
    similarDoc.setTitle("Similar Document");
    similarDoc.setContent("This is similar content");

    List<Map.Entry<UUID, Double>> similarDocuments = new ArrayList<>();
    similarDocuments.add(new AbstractMap.SimpleEntry<>(documentId, 0.95)); // Original document
    similarDocuments.add(new AbstractMap.SimpleEntry<>(similarDocId, 0.85)); // Similar document

    when(indexService.findSimilarDocuments(eq(testVector), anyInt(), anyDouble()))
        .thenReturn(similarDocuments);
    when(documentRepository.findAllById(anyList()))
        .thenReturn(Arrays.asList(testDocument, similarDoc));

    // Act
    List<SearchResult> results = searchService.findSimilarDocuments(documentId, 10, 0.7);

    // Assert
    assertFalse(results.isEmpty());
    assertEquals(1, results.size()); // Should exclude the original document
    assertEquals(similarDocId, results.get(0).getId());
    assertEquals("Similar Document", results.get(0).getTitle());
    assertEquals(0.85, results.get(0).getScore(), 0.001);

    verify(documentRepository).findById(documentId);
    verify(embeddingService).embed(testDocument.getContent());
    verify(indexService).findSimilarDocuments(eq(testVector), eq(11), eq(0.7)); // Limit + 1
    verify(documentRepository).findAllById(anyList());
  }

  @Test
  void testFindSimilarDocuments_DocumentNotFound() {
    // Arrange
    when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

    // Act
    List<SearchResult> results = searchService.findSimilarDocuments(documentId, 10, 0.7);

    // Assert
    assertTrue(results.isEmpty());
    verify(documentRepository).findById(documentId);
    verify(embeddingService, never()).embed(anyString());
    verify(indexService, never()).findSimilarDocuments(any(), anyInt(), anyDouble());
  }
}
