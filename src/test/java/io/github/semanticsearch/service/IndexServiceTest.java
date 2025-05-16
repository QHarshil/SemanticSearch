package io.github.semanticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IndexServiceTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient indicesClient;

    @Mock
    private co.elastic.clients.elasticsearch.core.ElasticsearchCoreClient coreClient;

    @InjectMocks
    private IndexService indexService;

    private Document testDocument;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(indexService, "indexName", "test-index");
        ReflectionTestUtils.setField(indexService, "dimensions", 1536);

        testDocument = new Document();
        testDocument.setId(UUID.randomUUID());
        testDocument.setTitle("Test Document");
        testDocument.setContent("This is a test document content");
        testDocument.setContentHash("test-hash");
        testDocument.setMetadata(Map.of("key", "value"));
    }

    @Test
    void testInitializeIndex_Success() throws IOException {
        // Arrange
        BooleanResponse existsResponse = mock(BooleanResponse.class);
        when(existsResponse.value()).thenReturn(false);
        
        CreateIndexResponse createResponse = mock(CreateIndexResponse.class);
        when(createResponse.acknowledged()).thenReturn(true);
        
        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        when(indicesClient.exists(any())).thenReturn(existsResponse);
        when(indicesClient.create(any())).thenReturn(createResponse);
        
        // Act
        indexService.initializeIndex();
        
        // Assert
        verify(indicesClient).exists(any());
        verify(indicesClient).create(any());
    }

    @Test
    void testInitializeIndex_AlreadyExists() throws IOException {
        // Arrange
        BooleanResponse existsResponse = mock(BooleanResponse.class);
        when(existsResponse.value()).thenReturn(true);
        
        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        when(indicesClient.exists(any())).thenReturn(existsResponse);
        
        // Act
        indexService.initializeIndex();
        
        // Assert
        verify(indicesClient).exists(any());
        verify(indicesClient, never()).create(any());
    }

    @Test
    void testIndexDocument_Success() throws IOException {
        // Arrange
        List<Float> embedding = Arrays.asList(0.1f, 0.2f, 0.3f);
        when(embeddingService.embed(anyString())).thenReturn(embedding);
        
        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.result()).thenReturn(IndexResponse.Result.Created);
        
        when(elasticsearchClient.index(any())).thenReturn(indexResponse);
        when(documentRepository.save(any(Document.class))).thenReturn(testDocument);
        
        // Act
        Document result = indexService.indexDocument(testDocument);
        
        // Assert
        assertNotNull(result);
        assertTrue(result.isIndexed());
        assertNotNull(result.getVectorId());
        verify(embeddingService).embed(testDocument.getContent());
        verify(elasticsearchClient).index(any());
        verify(documentRepository).save(testDocument);
    }

    @Test
    void testIndexDocument_EmptyEmbedding() {
        // Arrange
        when(embeddingService.embed(anyString())).thenReturn(Collections.emptyList());
        
        // Act
        Document result = indexService.indexDocument(testDocument);
        
        // Assert
        assertNotNull(result);
        assertFalse(result.isIndexed());
        assertNull(result.getVectorId());
        verify(embeddingService).embed(testDocument.getContent());
        verify(elasticsearchClient, never()).index(any());
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    void testDeleteDocumentVector_Success() throws IOException {
        // Arrange
        String vectorId = "test-vector-id";
        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        when(deleteResponse.result()).thenReturn(DeleteResponse.Result.Deleted);
        
        when(elasticsearchClient.delete(any())).thenReturn(deleteResponse);
        
        // Act
        boolean result = indexService.deleteDocumentVector(vectorId);
        
        // Assert
        assertTrue(result);
        verify(elasticsearchClient).delete(any());
    }

    @Test
    void testFindSimilarDocuments_Success() throws IOException {
        // Arrange
        List<Float> queryVector = Arrays.asList(0.1f, 0.2f, 0.3f);
        int limit = 10;
        float minScore = 0.7f;
        
        SearchResponse<Map> searchResponse = mock(SearchResponse.class);
        co.elastic.clients.elasticsearch.core.search.HitsMetadata<Map> hitsMetadata = mock(co.elastic.clients.elasticsearch.core.search.HitsMetadata.class);
        
        Hit<Map> hit = mock(Hit.class);
        when(hit.score()).thenReturn(0.85f);
        when(hit.source()).thenReturn(Map.of("document_id", testDocument.getId().toString()));
        
        when(hitsMetadata.hits()).thenReturn(List.of(hit));
        when(searchResponse.hits()).thenReturn(hitsMetadata);
        
        when(elasticsearchClient.search(any(), eq(Map.class))).thenReturn(searchResponse);
        
        // Act
        List<Map.Entry<UUID, Float>> results = indexService.findSimilarDocuments(queryVector, limit, minScore);
        
        // Assert
        assertFalse(results.isEmpty());
        assertEquals(1, results.size());
        assertEquals(testDocument.getId(), results.get(0).getKey());
        assertEquals(0.85f, results.get(0).getValue());
        verify(elasticsearchClient).search(any(), eq(Map.class));
    }
}
