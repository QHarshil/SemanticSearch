package io.github.semanticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;

/**
 * Service for indexing and managing document vectors in Elasticsearch.
 * Handles document indexing, updating, and deletion.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IndexService {

    private final ElasticsearchClient elasticsearchClient;
    private final EmbeddingService embeddingService;
    private final DocumentRepository documentRepository;

    @Value("${elasticsearch.index.name:semantic-search}")
    private String indexName;

    @Value("${elasticsearch.index.dimensions:1536}")
    private int dimensions;

    /**
     * Initialize the Elasticsearch index if it doesn't exist.
     * Sets up the vector search capabilities.
     */
    public void initializeIndex() {
        try {
            BooleanResponse existsResponse = elasticsearchClient.indices().exists(
                    ExistsRequest.of(e -> e.index(indexName))
            );

            if (!existsResponse.value()) {
                log.info("Creating Elasticsearch index: {}", indexName);
                
                CreateIndexResponse createResponse = elasticsearchClient.indices().create(
                    CreateIndexRequest.of(c -> c
                        .index(indexName)
                        .settings(IndexSettings.of(s -> s
                            .numberOfShards("3")
                            .numberOfReplicas("1")
                        ))
                        .mappings(m -> m
                            .properties("vector", p -> p
                                .denseVector(v -> v
                                    .dims(dimensions)
                                    .index(true)
                                    .similarity("cosine")
                                )
                            )
                            .properties("document_id", p -> p
                                .keyword(k -> k)
                            )
                            .properties("content_hash", p -> p
                                .keyword(k -> k)
                            )
                        )
                    )
                );
                
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
     * Index a document in Elasticsearch.
     * Generates embedding vector and stores it in Elasticsearch.
     *
     * @param document Document to index
     * @return Updated document with vector ID
     */
    @Transactional
    public Document indexDocument(Document document) {
        try {
            // Generate embedding for document content
            List<Float> embedding = embeddingService.embed(document.getContent());
            if (embedding.isEmpty()) {
                log.error("Failed to generate embedding for document: {}", document.getId());
                return document;
            }

            // Create vector document in Elasticsearch
            String vectorId = UUID.randomUUID().toString();
            IndexResponse response = elasticsearchClient.index(i -> i
                .index(indexName)
                .id(vectorId)
                .document(Map.of(
                    "vector", embedding,
                    "document_id", document.getId().toString(),
                    "content_hash", document.getContentHash()
                ))
            );

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
     * Update document index in Elasticsearch.
     * Deletes old vector and creates new one with updated content.
     *
     * @param document Document to update
     * @return Updated document
     */
    @Transactional
    public Document updateDocumentIndex(Document document) {
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
        try {
            DeleteResponse response = elasticsearchClient.delete(d -> d
                .index(indexName)
                .id(vectorId)
            );
            
            log.info("Document vector deleted: {}, result: {}", vectorId, response.result());
            return !response.result().equals(DeleteResponse.Result.NotFound);
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
    public List<Map.Entry<UUID, Float>> findSimilarDocuments(List<Float> queryVector, int limit, float minScore) {
        try {
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                .index(indexName)
                .query(q -> q
                    .scriptScore(ss -> ss
                        .query(sq -> sq.matchAll(m -> m))
                        .script(sc -> sc
                            .source("cosineSimilarity(params.query_vector, 'vector') + 1.0")
                            .params("query_vector", queryVector)
                        )
                    )
                )
                .size(limit)
                .minScore(minScore)
            , Map.class);

            List<Map.Entry<UUID, Float>> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source != null && source.containsKey("document_id")) {
                    String documentId = (String) source.get("document_id");
                    results.add(new AbstractMap.SimpleEntry<>(
                        UUID.fromString(documentId),
                        hit.score()
                    ));
                }
            }
            
            return results;
        } catch (IOException e) {
            log.error("Failed to find similar documents", e);
            return Collections.emptyList();
        }
    }
}
