package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.model.SearchRequest;
import io.github.semanticsearch.model.SearchResult;
import io.github.semanticsearch.support.InMemoryDocumentRepository;

class SearchServiceTest {

  private InMemoryDocumentRepository repository;
  private StubEmbeddingService embeddingService;
  private StubIndexService indexService;
  private SearchService searchService;
  private Document testDocument;
  private UUID documentId;
  private List<Double> testVector;

  @BeforeEach
  void setUp() {
    repository = new InMemoryDocumentRepository();
    documentId = UUID.randomUUID();
    testVector = Arrays.asList(0.1, 0.2, 0.3);

    testDocument = new Document();
    testDocument.setId(documentId);
    testDocument.setTitle("Test Document");
    testDocument.setContent("This is a test document content");
    testDocument.setContentHash("test-hash");
    testDocument.setMetadata(Map.of("key", "value"));
    repository.save(testDocument);

    embeddingService = new StubEmbeddingService(testVector);
    indexService = new StubIndexService();
    searchService = new SearchService(embeddingService, indexService, repository);
  }

  @Test
  void search_returnsResultsWithHighlights() {
    indexService.nextResults = List.of(new AbstractMap.SimpleEntry<>(documentId, 0.85));
    SearchRequest request =
        SearchRequest.builder()
            .query("test query")
            .limit(10)
            .minScore(0.7)
            .includeContent(true)
            .includeHighlights(true)
            .build();

    List<SearchResult> results = searchService.search(request);

    assertEquals(1, results.size());
    SearchResult result = results.get(0);
    assertEquals(documentId, result.getId());
    assertEquals("Test Document", result.getTitle());
    assertEquals(testDocument.getContent(), result.getContent());
    assertEquals(0.85, result.getScore());
    assertFalse(result.getHighlights().isEmpty());
    assertEquals(testVector, embeddingService.lastTextEmbedding);
    assertEquals(testVector, indexService.lastQueryVector);
    assertEquals(10, indexService.lastLimit);
    assertEquals(0.7, indexService.lastMinScore);
  }

  @Test
  void search_returnsEmptyWhenEmbeddingFails() {
    embeddingService.nextEmbedding = Collections.emptyList();
    SearchRequest request = SearchRequest.builder().query("bad").limit(5).minScore(0.7).build();

    List<SearchResult> results = searchService.search(request);

    assertTrue(results.isEmpty());
  }

  @Test
  void search_returnsEmptyWhenNoSimilarDocuments() {
    indexService.nextResults = Collections.emptyList();
    SearchRequest request = SearchRequest.builder().query("test query").limit(10).minScore(0.7).build();

    List<SearchResult> results = searchService.search(request);

    assertTrue(results.isEmpty());
    assertEquals(10, indexService.lastLimit);
  }

  @Test
  void findSimilarDocuments_excludesOriginalDocument() {
    UUID similarId = UUID.randomUUID();
    Document similarDoc = new Document();
    similarDoc.setId(similarId);
    similarDoc.setTitle("Similar Document");
    similarDoc.setContent("This is similar content");
    repository.save(similarDoc);

    embeddingService.nextEmbedding = testVector;
    indexService.nextResults =
        List.of(
            new AbstractMap.SimpleEntry<>(documentId, 0.95),
            new AbstractMap.SimpleEntry<>(similarId, 0.85));

    List<SearchResult> results = searchService.findSimilarDocuments(documentId, 5, 0.5);

    assertEquals(1, results.size());
    assertEquals(similarId, results.getFirst().getId());
    assertEquals(6, indexService.lastLimit); // limit + 1 applied
  }

  @Test
  void findSimilarDocuments_returnsEmptyWhenMissingDocument() {
    repository.deleteById(documentId);
    List<SearchResult> results = searchService.findSimilarDocuments(documentId, 3, 0.7);
    assertTrue(results.isEmpty());
  }

  private static class StubEmbeddingService extends EmbeddingService {
    List<Double> nextEmbedding;
    List<Double> lastTextEmbedding;

    StubEmbeddingService(List<Double> nextEmbedding) {
      super(null);
      this.nextEmbedding = nextEmbedding;
    }

    @Override
    public List<Double> embed(String text) {
      lastTextEmbedding = nextEmbedding;
      return nextEmbedding;
    }
  }

  private static class StubIndexService extends IndexService {
    List<Map.Entry<UUID, Double>> nextResults = Collections.emptyList();
    List<Double> lastQueryVector;
    int lastLimit;
    double lastMinScore;

    StubIndexService() {
      super(null, null, null);
    }

    @Override
    public List<Map.Entry<UUID, Double>> findSimilarDocuments(
        List<Double> queryVector, int limit, double minScore) {
      this.lastQueryVector = queryVector;
      this.lastLimit = limit;
      this.lastMinScore = minScore;
      return nextResults;
    }
  }
}
