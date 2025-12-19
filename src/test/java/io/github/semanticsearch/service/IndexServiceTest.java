package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.support.InMemoryDocumentRepository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.Endpoint;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.TransportOptions;
import co.elastic.clients.transport.endpoints.BooleanResponse;

class IndexServiceTest {

  private InMemoryDocumentRepository repository;
  private StubEmbeddingService embeddingService;
  private RecordingTransport transport;
  private IndexService indexService;
  private Document testDocument;

  @BeforeEach
  void setUp() {
    repository = new InMemoryDocumentRepository();
    embeddingService = new StubEmbeddingService(List.of(0.1, 0.2, 0.3));
    transport = new RecordingTransport();
    ElasticsearchClient client = new ElasticsearchClient(transport);
    indexService = new IndexService(client, embeddingService, repository);
    ReflectionTestUtils.setField(indexService, "indexName", "test-index");
    ReflectionTestUtils.setField(indexService, "dimensions", 3);

    testDocument = new Document();
    testDocument.setId(UUID.randomUUID());
    testDocument.setTitle("Test Document");
    testDocument.setContent("This is a test document content");
    testDocument.setContentHash("test-hash");
  }

  @Test
  void initializeIndexCreatesIndexWhenMissing() {
    transport.existsResponse = new BooleanResponse(false);
    indexService.initializeIndex();
    assertTrue(transport.createIndexCalled);
  }

  @Test
  void initializeIndexSkipsWhenExists() {
    transport.existsResponse = new BooleanResponse(true);
    indexService.initializeIndex();
    assertFalse(transport.createIndexCalled);
  }

  @Test
  void indexDocumentSavesVectorId() {
    repository.save(testDocument);
    Document result = indexService.indexDocument(testDocument);

    assertTrue(result.isIndexed());
    assertNotNull(result.getVectorId());
    assertEquals(1, repository.count());
    assertEquals(Result.Created, transport.indexResponse.result());
  }

  @Test
  void indexDocumentReturnsOriginalWhenEmbeddingFails() {
    embeddingService.nextEmbedding = Collections.emptyList();
    Document result = indexService.indexDocument(testDocument);
    assertFalse(result.isIndexed());
    assertNull(result.getVectorId());
  }

  @Test
  void deleteDocumentVectorReturnsTrue() {
    boolean deleted = indexService.deleteDocumentVector("vector-123");
    assertTrue(deleted);
    assertTrue(transport.deleteCalled);
  }

  @Test
  void findSimilarDocumentsMapsSearchHits() {
    UUID similarId = UUID.randomUUID();
    transport.setSearchHit(similarId.toString(), 0.9);

    List<Map.Entry<UUID, Double>> results =
        indexService.findSimilarDocuments(List.of(0.1, 0.2, 0.3), 5, 0.2);

    assertEquals(1, results.size());
    assertEquals(similarId, results.getFirst().getKey());
    assertEquals(0.9, results.getFirst().getValue());
    assertTrue(transport.searchCalled);
  }

  private static class StubEmbeddingService extends EmbeddingService {
    List<Double> nextEmbedding;

    StubEmbeddingService(List<Double> nextEmbedding) {
      super(null);
      this.nextEmbedding = nextEmbedding;
    }

    @Override
    public List<Double> embed(String text) {
      return nextEmbedding;
    }
  }

  private static class RecordingTransport implements ElasticsearchTransport {
    BooleanResponse existsResponse = new BooleanResponse(false);
    boolean createIndexCalled = false;
    boolean deleteCalled = false;
    boolean searchCalled = false;
    IndexResponse indexResponse =
        IndexResponse.of(
            b ->
                b.result(Result.Created)
                    .id("vector-id")
                    .index("test-index")
                    .version(1)
                    .seqNo(1)
                    .primaryTerm(1)
                    .shards(s -> s.total(1).successful(1).failed(0)));
    DeleteResponse deleteResponse =
        DeleteResponse.of(
            b ->
                b.result(Result.Deleted)
                    .id("vector-id")
                    .index("test-index")
                    .version(1)
                    .seqNo(1)
                    .primaryTerm(1)
                    .shards(s -> s.total(1).successful(1).failed(0)));
    SearchResponse<Map> searchResponse =
        SearchResponse.of(
            b ->
                b.took(1)
                    .timedOut(false)
                    .shards(s -> s.total(1).successful(1).failed(0))
                    .hits(h -> h.hits(List.of())));
    private final JsonpMapper mapper = new JacksonJsonpMapper();
    private final TransportOptions emptyOptions =
        new TransportOptions() {
          @Override
          public java.util.Collection<java.util.Map.Entry<String, String>> headers() {
            return List.of();
          }

          @Override
          public Map<String, String> queryParameters() {
            return Map.of();
          }

          @Override
          public java.util.function.Function<List<String>, Boolean> onWarnings() {
            return warnings -> true;
          }

          @Override
          public Builder toBuilder() {
            return null;
          }
        };

    void setSearchHit(String documentId, double score) {
      Hit<Map> hit =
          Hit.of(
              h ->
                  h.source(Map.of("document_id", documentId))
                      .score(score)
                      .index("test-index")
                      .id(documentId));
      this.searchResponse =
          SearchResponse.of(
              b ->
                  b.took(1)
                      .timedOut(false)
                      .shards(s -> s.total(1).successful(1).failed(0))
                      .hits(h -> h.hits(hit)));
    }

    @Override
    public <RequestT, ResponseT, ErrorT> ResponseT performRequest(
        RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, TransportOptions options)
        throws IOException {
      return castResponse(endpoint.id());
    }

    @Override
    public <RequestT, ResponseT, ErrorT> CompletableFuture<ResponseT> performRequestAsync(
        RequestT request,
        Endpoint<RequestT, ResponseT, ErrorT> endpoint,
        TransportOptions options) {
      return CompletableFuture.completedFuture(castResponse(endpoint.id()));
    }

    @SuppressWarnings("unchecked")
    private <ResponseT> ResponseT castResponse(String endpointId) {
      switch (endpointId) {
        case "es/indices.exists":
          return (ResponseT) existsResponse;
        case "es/indices.create":
          createIndexCalled = true;
          return (ResponseT)
              CreateIndexResponse.of(
                  b -> b.acknowledged(true).shardsAcknowledged(true).index("test-index"));
        case "es/index":
          return (ResponseT) indexResponse;
        case "es/delete":
          deleteCalled = true;
          return (ResponseT) deleteResponse;
        case "es/search":
          searchCalled = true;
          return (ResponseT) searchResponse;
        default:
          throw new IllegalArgumentException("Unexpected endpoint: " + endpointId);
      }
    }

    @Override
    public JsonpMapper jsonpMapper() {
      return mapper;
    }

    @Override
    public TransportOptions options() {
      return emptyOptions;
    }

    @Override
    public void close() throws IOException {}
  }
}
