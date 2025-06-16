package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.util.ObjectBuilder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class IndexServiceTest {

  @Mock private ElasticsearchClient elasticsearchClient;

  @Mock private EmbeddingService embeddingService;

  @Mock private DocumentRepository documentRepository;

  @Mock private co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient indicesClient;

  @InjectMocks private IndexService indexService;

  private Document testDocument;
  private BooleanResponse existsResponse;
  private CreateIndexResponse createResponse;
  private IndexResponse indexResponse;
  private DeleteResponse deleteResponse;
  private SearchResponse<Map> searchResponse;

  @BeforeEach
  void setUp() throws IOException {
    ReflectionTestUtils.setField(indexService, "indexName", "test-index");
    ReflectionTestUtils.setField(indexService, "dimensions", 1536);

    testDocument = new Document();
    testDocument.setId(UUID.randomUUID());
    testDocument.setTitle("Test Document");
    testDocument.setContent("This is a test document content");
    testDocument.setContentHash("test-hash");
    testDocument.setMetadata(Map.of("key", "value"));

    // Set up the indices client mock to avoid NPE
    when(elasticsearchClient.indices()).thenReturn(indicesClient);

    // Create all mock responses
    existsResponse = mock(BooleanResponse.class);
    createResponse = mock(CreateIndexResponse.class);
    indexResponse = mock(IndexResponse.class);
    deleteResponse = mock(DeleteResponse.class);
    searchResponse = mock(SearchResponse.class);

    // Set up default behaviors
    when(createResponse.acknowledged()).thenReturn(true);
    when(indexResponse.result()).thenReturn(Result.Created);
    when(deleteResponse.result()).thenReturn(Result.Deleted);

    // Set up mock responses with explicit method signatures
    // For indices.exists
    when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(existsResponse);

    // For indices.create
    when(indicesClient.create(any(CreateIndexRequest.class))).thenReturn(createResponse);

    // For index - using Function-based signature to match lambda usage
    doAnswer(invocation -> indexResponse).when(elasticsearchClient).<Map>index(any(Function.class));

    // For delete - using Function-based signature to match lambda usage
    doAnswer(invocation -> deleteResponse).when(elasticsearchClient).delete(any(Function.class));

    // For search - using Function-based signature to match lambda usage
    doAnswer(invocation -> searchResponse)
        .when(elasticsearchClient)
        .<Map>search(any(Function.class), eq(Map.class));
  }

  @Test
  void testInitializeIndex_Success() throws IOException {
    // Arrange
    when(existsResponse.value()).thenReturn(false);

    // Act
    indexService.initializeIndex();

    // Assert
    verify(indicesClient).exists(any(ExistsRequest.class));
    verify(indicesClient).create(any(CreateIndexRequest.class));
  }

  @Test
  void testInitializeIndex_AlreadyExists() throws IOException {
    // Arrange
    when(existsResponse.value()).thenReturn(true);

    // Act
    indexService.initializeIndex();

    // Assert
    verify(indicesClient).exists(any(ExistsRequest.class));
    verify(indicesClient, never()).create(any(CreateIndexRequest.class));
  }

  @Test
  void testIndexDocument_Success() throws IOException {
    // Arrange
    List<Double> embedding = Arrays.asList(0.1, 0.2, 0.3);
    when(embeddingService.embed(anyString())).thenReturn(embedding);
    when(documentRepository.save(any(Document.class))).thenReturn(testDocument);

    // Mock the index method using doAnswer to handle the lambda-based call
    doAnswer(
            invocation -> {
              // Extract the function argument (lambda)
              Function<IndexRequest.Builder<Map>, ObjectBuilder<IndexRequest<Map>>> function =
                  invocation.getArgument(0);

              // Return the mocked response
              return indexResponse;
            })
        .when(elasticsearchClient)
        .<Map>index(any(Function.class));

    when(indexResponse.result()).thenReturn(Result.Created);

    // Act
    Document result = indexService.indexDocument(testDocument);

    // Assert
    assertNotNull(result);
    assertTrue(result.isIndexed());
    assertNotNull(result.getVectorId());
    verify(embeddingService).embed(testDocument.getContent());
    verify(elasticsearchClient).<Map>index(any(Function.class));
    verify(documentRepository).save(testDocument);
  }

  @Test
  void testIndexDocument_EmptyEmbedding() throws IOException {
    // Arrange
    when(embeddingService.embed(anyString())).thenReturn(Collections.emptyList());

    // Act
    Document result = indexService.indexDocument(testDocument);

    // Assert
    assertNotNull(result);
    assertFalse(result.isIndexed());
    assertNull(result.getVectorId());
    verify(embeddingService).embed(testDocument.getContent());
    verify(elasticsearchClient, never()).<Map>index(any(Function.class));
    verify(documentRepository, never()).save(any(Document.class));
  }

  @Test
  void testDeleteDocumentVector_Success() throws IOException {
    // Arrange
    String vectorId = "test-vector-id";

    // Mock the delete method using doAnswer to handle the lambda-based call
    doAnswer(
            invocation -> {
              // Extract the function argument (lambda)
              Function<DeleteRequest.Builder, ObjectBuilder<DeleteRequest>> function =
                  invocation.getArgument(0);

              // Return the mocked response
              return deleteResponse;
            })
        .when(elasticsearchClient)
        .delete(any(Function.class));

    when(deleteResponse.result()).thenReturn(Result.Deleted);

    // Act
    boolean result = indexService.deleteDocumentVector(vectorId);

    // Assert
    assertTrue(result);
    verify(elasticsearchClient).delete(any(Function.class));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  void testFindSimilarDocuments_Success() throws IOException {
    // Arrange
    List<Double> queryVector = Arrays.asList(0.1, 0.2, 0.3);
    int limit = 10;
    double minScore = 0.7;

    // Create properly mocked search response objects
    HitsMetadata hitsMetadata = mock(HitsMetadata.class);

    Hit hit = mock(Hit.class);
    when(hit.score()).thenReturn(0.85);
    when(hit.source()).thenReturn(Map.of("document_id", testDocument.getId().toString()));

    List<Hit> hitsList = new ArrayList<>();
    hitsList.add(hit);

    when(hitsMetadata.hits()).thenReturn(hitsList);

    // Ensure searchResponse is properly mocked with a non-null hits result
    when(searchResponse.hits()).thenReturn(hitsMetadata);

    // Mock the search method using doAnswer to handle the lambda-based call
    doAnswer(
            invocation -> {
              // Extract the function argument (lambda)
              Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>> function =
                  invocation.getArgument(0);

              // Return the mocked response
              return searchResponse;
            })
        .when(elasticsearchClient)
        .<Map>search(any(Function.class), eq(Map.class));

    // Act
    List<Map.Entry<UUID, Double>> results =
        indexService.findSimilarDocuments(queryVector, limit, minScore);

    // Assert
    assertFalse(results.isEmpty());
    assertEquals(1, results.size());
    assertEquals(testDocument.getId(), results.get(0).getKey());
    assertEquals(0.85, results.get(0).getValue());
    verify(elasticsearchClient).<Map>search(any(Function.class), eq(Map.class));
  }
}
