package io.github.semanticsearch.service;

import java.io.IOException;
import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.transport.endpoints.BooleanResponse;
/**
 * Service for indexing and managing document vectors in Elasticsearch. Handles document indexing,
 * updating, and deletion.
 */
@Service
public class IndexService {

  private static final Logger log = LoggerFactory.getLogger(IndexService.class);

  private final ElasticsearchClient elasticsearchClient;
  private final EmbeddingService embeddingService;
  private final DocumentRepository documentRepository;

  @Value("${elasticsearch.index.name:semantic-search}")
  private String indexName;

  @Value("${elasticsearch.index.dimensions:1536}")
  private int dimensions;

  @Value("${elasticsearch.stub-enabled:false}")
  private boolean stubEnabled;

  private final ConcurrentMap<String, List<Double>> stubVectors = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, String> stubDocToVector = new ConcurrentHashMap<>();

  public IndexService(
      ElasticsearchClient elasticsearchClient,
      EmbeddingService embeddingService,
      DocumentRepository documentRepository) {
    this.elasticsearchClient = elasticsearchClient;
    this.embeddingService = embeddingService;
    this.documentRepository = documentRepository;
  }

  /**
   * Initialize the Elasticsearch index if it doesn't exist. Sets up the vector search capabilities.
   */
  public void initializeIndex() {
    if (stubEnabled) {
      log.info("Elasticsearch stub enabled; skipping remote index initialization");
      return;
    }
    try {
      BooleanResponse existsResponse =
          elasticsearchClient.indices().exists(ExistsRequest.of(e -> e.index(indexName)));

      if (!existsResponse.value()) {
        log.info("Creating Elasticsearch index: {}", indexName);

        CreateIndexResponse createResponse =
            elasticsearchClient
                .indices()
                .create(
                    CreateIndexRequest.of(
                        c ->
                            c.index(indexName)
                                .settings(
                                    IndexSettings.of(
                                        s -> s.numberOfShards("3").numberOfReplicas("1")))
                                .mappings(
                                    m ->
                                        m.properties(
                                                "vector",
                                                p ->
                                                    p.denseVector(
                                                        v ->
                                                            v.dims(dimensions)
                                                                .index(true)
                                                                .similarity("cosine")))
                                            .properties("document_id", p -> p.keyword(k -> k))
                                            .properties("content_hash", p -> p.keyword(k -> k)))));

        log.info("Index created: {}, acknowledged: {}", indexName, createResponse.acknowledged());
      } else {
        log.info("Index already exists: {}", indexName);
      }
    } catch (IOException e) {
      log.error("Failed to initialize Elasticsearch index", e);
      throw new RuntimeException("Failed to initialize Elasticsearch index", e);
    }
  }

  /**
   * Index a document in Elasticsearch. Generates embedding vector and stores it in Elasticsearch.
   *
   * @param document Document to index
   * @return Updated document with vector ID
   */
  @Transactional
  public Document indexDocument(Document document) {
    if (stubEnabled) {
      return indexDocumentInStub(document);
    }
    try {
      // Generate embedding for document content
      List<Double> embedding = embeddingService.embed(document.getContent());
      if (embedding.isEmpty()) {
        log.error("Failed to generate embedding for document: {}", document.getId());
        return document;
      }

      // Create vector document in Elasticsearch
      String vectorId = UUID.randomUUID().toString();
      IndexResponse response =
          elasticsearchClient.index(
              i ->
                  i.index(indexName)
                      .id(vectorId)
                      .document(
                          Map.of(
                              "vector", co.elastic.clients.json.JsonData.of(embedding),
                              "document_id", document.getId().toString(),
                              "content_hash", document.getContentHash())));

      log.info("Document indexed in Elasticsearch: {}, result: {}", vectorId, response.result());

      // Update document with vector ID and indexed status
      document.setVectorId(vectorId);
      document.setIndexed(true);
      return documentRepository.save(document);
    } catch (IOException e) {
      log.error("Failed to index document: {}", document.getId(), e);
      throw new RuntimeException("Failed to index document", e);
    }
  }

  /**
   * Update document index in Elasticsearch. Deletes old vector and creates new one with updated
   * content.
   *
   * @param document Document to update
   * @return Updated document
   */
  @Transactional
  public Document updateDocumentIndex(Document document) {
    if (stubEnabled) {
      if (document.getVectorId() != null) {
        deleteDocumentVector(document.getVectorId());
      }
      return indexDocumentInStub(document);
    }
    try {
      // Delete old vector if exists
      if (document.getVectorId() != null) {
        deleteDocumentVector(document.getVectorId());
      }

      // Create new vector
      return indexDocument(document);
    } catch (Exception e) {
      log.error("Failed to update document index: {}", document.getId(), e);
      throw new RuntimeException("Failed to update document index", e);
    }
  }

  /**
   * Delete document vector from Elasticsearch.
   *
   * @param vectorId Vector ID to delete
   * @return True if deletion was successful
   */
  public boolean deleteDocumentVector(String vectorId) {
    if (stubEnabled) {
      stubVectors.remove(vectorId);
      stubDocToVector.entrySet().removeIf(e -> e.getValue().equals(vectorId));
      log.info("Stub document vector deleted: {}", vectorId);
      return true;
    }
    try {
      DeleteResponse response = elasticsearchClient.delete(d -> d.index(indexName).id(vectorId));

      log.info("Document vector deleted: {}, result: {}", vectorId, response.result());
      return response.result() != co.elastic.clients.elasticsearch._types.Result.NotFound;
    } catch (IOException e) {
      log.error("Failed to delete document vector: {}", vectorId, e);
      return false;
    }
  }

  /**
   * Find similar documents based on a query vector.
   *
   * @param queryVector Query vector to find similar documents
   * @param limit Maximum number of results to return
   * @param minScore Minimum similarity score threshold
   * @return List of document IDs with similarity scores
   */
  public List<Map.Entry<UUID, Double>> findSimilarDocuments(
      List<Double> queryVector, int limit, double minScore) {
    if (stubEnabled) {
      return findSimilarInStub(queryVector, limit, minScore);
    }
    try {
      SearchResponse<Map> response =
          elasticsearchClient.search(
              s ->
                  s.index(indexName)
                      .query(
                          q ->
                              q.scriptScore(
                                  ss ->
                                      ss.query(sq -> sq.matchAll(m -> m))
                                          .script(
                                              sc ->
                                                  sc.inline(
                                                      i ->
                                                          i.source(
                                                                  "cosineSimilarity(params.query_vector, 'vector') + 1.0")
                                                              .params(
                                                                  Map.of(
                                                                      "query_vector",
                                                                      co.elastic.clients.json
                                                                          .JsonData.of(
                                                                          queryVector)))))))
                      .size(limit)
                      .minScore(minScore),
              Map.class);

      List<Map.Entry<UUID, Double>> results = new ArrayList<>();
      for (Hit<Map> hit : response.hits().hits()) {
        Map<String, Object> source = hit.source();
        if (source != null && source.containsKey("document_id")) {
          String documentId = (String) source.get("document_id");
          results.add(new AbstractMap.SimpleEntry<>(UUID.fromString(documentId), hit.score()));
        }
      }

      return results;
    } catch (IOException e) {
      log.error("Failed to find similar documents", e);
      return Collections.emptyList();
    }
  }

  private Document indexDocumentInStub(Document document) {
    List<Double> embedding = embeddingService.embed(document.getContent());
    if (embedding.isEmpty()) {
      log.error("Failed to generate embedding for document: {}", document.getId());
      return document;
    }

    String vectorId = UUID.randomUUID().toString();
    stubVectors.put(vectorId, embedding);
    stubDocToVector.put(document.getId(), vectorId);

    document.setVectorId(vectorId);
    document.setIndexed(true);
    return documentRepository.save(document);
  }

  private List<Map.Entry<UUID, Double>> findSimilarInStub(
      List<Double> queryVector, int limit, double minScore) {
    List<Map.Entry<UUID, Double>> results = new ArrayList<>();
    stubVectors.forEach(
        (vectorId, stored) -> {
          double score = cosineSimilarity(queryVector, stored);
          if (score >= minScore) {
            Optional<UUID> docId =
                stubDocToVector.entrySet().stream()
                    .filter(e -> e.getValue().equals(vectorId))
                    .map(Map.Entry::getKey)
                    .findFirst();
            docId.ifPresent(id -> results.add(new AbstractMap.SimpleEntry<>(id, score)));
          }
        });

    return results.stream()
        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
        .limit(Math.max(1, limit))
        .toList();
  }

  private double cosineSimilarity(List<Double> a, List<Double> b) {
    if (a == null || b == null || a.isEmpty() || b.isEmpty() || a.size() != b.size()) {
      return 0.0;
    }
    double dot = 0.0;
    double normA = 0.0;
    double normB = 0.0;
    for (int i = 0; i < a.size(); i++) {
      double av = a.get(i);
      double bv = b.get(i);
      dot += av * bv;
      normA += av * av;
      normB += bv * bv;
    }
    if (normA == 0 || normB == 0) {
      return 0.0;
    }
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
  }
}
