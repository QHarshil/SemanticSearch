package io.github.semanticsearch.service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
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
  private final CorpusStatistics corpusStatistics;

  @Value("${elasticsearch.index.name:semantic-search}")
  private String indexName;

  @Value("${elasticsearch.stub-enabled:false}")
  private boolean stubEnabled;

  /**
   * The in-memory index, keyed by vector id exactly as Elasticsearch is. Storing the document id
   * alongside each vector keeps lookups direct rather than scanning for a reverse mapping.
   */
  private final ConcurrentMap<String, StubVector> stubVectors = new ConcurrentHashMap<>();

  private record StubVector(UUID documentId, List<Double> vector) {}

  public IndexService(
      ElasticsearchClient elasticsearchClient,
      EmbeddingService embeddingService,
      DocumentRepository documentRepository,
      CorpusStatistics corpusStatistics) {
    this.elasticsearchClient = elasticsearchClient;
    this.embeddingService = embeddingService;
    this.documentRepository = documentRepository;
    this.corpusStatistics = corpusStatistics;
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
                                                            v.dims(embeddingService.dimensions())
                                                                .index(true)
                                                                .similarity("cosine")))
                                            .properties("document_id", p -> p.keyword(k -> k))
                                            .properties("content_hash", p -> p.keyword(k -> k))
                                            // Flattened makes arbitrary metadata keys
                                            // filterable as exact terms with no mapping
                                            // change per key, which is what kNN
                                            // pre-filtering needs.
                                            .properties("metadata", p -> p.flattened(f -> f)))));

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
   * Discards the current index and re-embeds every stored document.
   *
   * <p>Use this after changing the embedding provider or dimension, when existing vectors are no
   * longer comparable to newly generated ones. Both caches are dropped as part of the rebuild: the
   * embedding cache because the vectors it holds are the very things being replaced, and the search
   * cache because every cached ranking was computed against the old index.
   *
   * @return the number of documents re-indexed
   */
  @CacheEvict(
      cacheNames = {"searchResults", "embeddings"},
      allEntries = true)
  @Transactional
  public int rebuildIndex() {
    if (stubEnabled) {
      stubVectors.clear();
    } else {
      dropIndex();
      initializeIndex();
    }
    corpusStatistics.invalidate();

    int reindexed = 0;
    for (Document document : documentRepository.findAll()) {
      document.setVectorId(null);
      document.setIndexed(false);
      indexDocument(document);
      reindexed++;
    }
    refreshIndex();
    log.info("Rebuilt search index over {} documents", reindexed);
    return reindexed;
  }

  /**
   * Make recent writes visible to search immediately.
   *
   * <p>Elasticsearch buffers new documents until its next refresh, a second by default, so a
   * document can be committed and reported as indexed slightly before a query can return it.
   * Individual writes are left on that default rather than forcing a refresh each time, which would
   * serialise indexing behind a segment flush per document. A rebuild refreshes once at the end
   * instead.
   */
  public void refreshIndex() {
    if (stubEnabled) {
      return;
    }
    try {
      elasticsearchClient.indices().refresh(r -> r.index(indexName));
    } catch (IOException e) {
      log.warn("Failed to refresh index {}", indexName, e);
    }
  }

  private void dropIndex() {
    try {
      BooleanResponse exists =
          elasticsearchClient.indices().exists(ExistsRequest.of(e -> e.index(indexName)));
      if (exists.value()) {
        elasticsearchClient.indices().delete(d -> d.index(indexName));
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to drop Elasticsearch index " + indexName, e);
    }
  }

  /**
   * Index a document, replacing any vector already held for it.
   *
   * <p>The vector id is the document id, which makes this an upsert: indexing the same document
   * twice overwrites one entry rather than accumulating duplicates that would each match a query
   * separately. That is what lets a failed or interrupted write simply be retried.
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
      List<Double> embedding = embeddingService.embed(Tokenizer.indexableText(document));
      if (embedding.isEmpty()) {
        log.error("Failed to generate embedding for document: {}", document.getId());
        return document;
      }

      String vectorId = vectorIdOf(document);
      IndexResponse response =
          elasticsearchClient.index(
              i -> i.index(indexName).id(vectorId).document(sourceOf(document, embedding)));

      log.info("Document indexed in Elasticsearch: {}, result: {}", vectorId, response.result());

      document.setVectorId(vectorId);
      document.setIndexed(true);
      return documentRepository.save(document);
    } catch (IOException e) {
      log.error("Failed to index document: {}", document.getId(), e);
      throw new RuntimeException("Failed to index document", e);
    }
  }

  /**
   * Re-index a document whose content changed.
   *
   * <p>Because {@link #indexDocument} upserts on the document id, this is a plain overwrite. There
   * is no delete-then-create step, which would otherwise leave the document unsearchable in the
   * window between the two calls and orphan its vector if the second one failed.
   *
   * @param document Document to update
   * @return Updated document
   */
  @Transactional
  public Document updateDocumentIndex(Document document) {
    // An edit leaves the document count unchanged, so the corpus statistics
    // cache cannot detect it by counting rows.
    corpusStatistics.invalidate();
    return indexDocument(document);
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
    return findSimilarDocuments(queryVector, limit, minScore, Map.of());
  }

  /**
   * Find the nearest documents to a query vector, optionally restricted by metadata.
   *
   * <p>Retrieval goes through Elasticsearch approximate kNN, which walks the HNSW graph built for
   * the {@code vector} field and visits {@code num_candidates} vectors per shard. Scoring every
   * vector in the index instead would make query cost grow linearly with the corpus, which defeats
   * the point of indexing the field for search in the first place.
   *
   * <p>Filters are handed to the kNN search rather than applied to its output, so they narrow the
   * graph traversal instead of discarding neighbours that were already found. Filtering afterwards
   * can return fewer than {@code limit} documents even when enough matching ones exist.
   *
   * @param queryVector Query vector to find similar documents
   * @param limit Maximum number of results to return
   * @param minScore Minimum cosine similarity, on the same [0,1] scale the in-memory index reports
   * @param filters Metadata equality filters applied before scoring
   * @return document ids with their cosine similarity, most similar first
   */
  public List<Map.Entry<UUID, Double>> findSimilarDocuments(
      List<Double> queryVector, int limit, double minScore, Map<String, String> filters) {
    if (stubEnabled) {
      return findSimilarInStub(queryVector, limit, minScore);
    }

    int size = Math.max(1, limit);
    List<Float> vector = queryVector.stream().map(Double::floatValue).toList();
    List<Query> preFilters = metadataFilters(filters);

    try {
      SearchResponse<ObjectNode> response =
          elasticsearchClient.search(
              s -> {
                s.index(indexName)
                    .knn(
                        k ->
                            k.field("vector")
                                .queryVector(vector)
                                .k(size)
                                .numCandidates(candidatePoolFor(size))
                                .filter(preFilters))
                    .size(size);
                // A threshold of zero excludes nothing, and passing it through would
                // still drop anti-correlated documents once converted, so it is left
                // unset rather than translated.
                if (minScore > 0.0) {
                  s.minScore(elasticsearchScoreOf(minScore));
                }
                return s;
              },
              ObjectNode.class);

      List<Map.Entry<UUID, Double>> results = new ArrayList<>();
      for (Hit<ObjectNode> hit : response.hits().hits()) {
        ObjectNode source = hit.source();
        if (source != null && source.hasNonNull("document_id")) {
          UUID documentId = UUID.fromString(source.get("document_id").asText());
          double score = hit.score() == null ? 0.0 : cosineOf(hit.score());
          results.add(new AbstractMap.SimpleEntry<>(documentId, score));
        }
      }

      return results;
    } catch (IOException e) {
      log.error("Failed to find similar documents", e);
      return Collections.emptyList();
    }
  }

  /**
   * Number of neighbours the HNSW traversal considers per shard. Higher values buy recall with
   * latency; Elasticsearch requires at least {@code k} and rejects anything above 10000.
   */
  private static int candidatePoolFor(int k) {
    return Math.min(10_000, Math.max(k, Math.max(100, k * 10)));
  }

  /**
   * Elasticsearch reports a cosine kNN hit as {@code (1 + cosine) / 2}, so an orthogonal vector
   * scores 0.5 rather than 0. These two conversions keep the scores this method returns, and the
   * thresholds it accepts, on the raw cosine scale the in-memory index uses - without them the same
   * {@code minScore} would mean two different things depending on which index was running.
   */
  private static double elasticsearchScoreOf(double cosine) {
    return (1.0 + cosine) / 2.0;
  }

  private static double cosineOf(double elasticsearchScore) {
    return Math.max(0.0, Math.min(1.0, 2.0 * elasticsearchScore - 1.0));
  }

  private static List<Query> metadataFilters(Map<String, String> filters) {
    if (filters == null || filters.isEmpty()) {
      return List.of();
    }
    List<Query> queries = new ArrayList<>();
    filters.forEach(
        (key, value) ->
            queries.add(
                Query.of(
                    q -> q.term(t -> t.field("metadata." + key).value(filterValueOf(value))))));
    return queries;
  }

  private Document indexDocumentInStub(Document document) {
    List<Double> embedding = embeddingService.embed(Tokenizer.indexableText(document));
    if (embedding.isEmpty()) {
      log.error("Failed to generate embedding for document: {}", document.getId());
      return document;
    }

    String vectorId = vectorIdOf(document);
    stubVectors.put(vectorId, new StubVector(document.getId(), embedding));

    document.setVectorId(vectorId);
    document.setIndexed(true);
    return documentRepository.save(document);
  }

  private List<Map.Entry<UUID, Double>> findSimilarInStub(
      List<Double> queryVector, int limit, double minScore) {
    List<Map.Entry<UUID, Double>> results = new ArrayList<>();
    stubVectors.forEach(
        (vectorId, stored) -> {
          double score = cosineSimilarity(queryVector, stored.vector());
          if (score >= minScore) {
            results.add(new AbstractMap.SimpleEntry<>(stored.documentId(), score));
          }
        });

    return results.stream()
        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
        .limit(Math.max(1, limit))
        .toList();
  }

  /**
   * The search index uses the document id as its own document id, so writes are upserts and the two
   * stores never need a separate mapping table to stay in step.
   */
  private static String vectorIdOf(Document document) {
    return document.getId().toString();
  }

  /**
   * The indexed document body, built from plain collections.
   *
   * <p>Values must not be wrapped in {@code JsonData}. The transport serialises this map with its
   * Jackson mapper, which sees a JsonData as an opaque bean with no properties and writes {@code
   * {}} - so the vector arrives as an empty object and Elasticsearch rejects the write while
   * parsing dense_vector, which expects an array of numbers. A {@code List<Double>} and a {@code
   * Map<String, String>} serialise to the array and object the mapping declares.
   */
  static Map<String, Object> sourceOf(Document document, List<Double> embedding) {
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("vector", embedding);
    source.put("document_id", document.getId().toString());
    source.put("content_hash", document.getContentHash());
    source.put("metadata", metadataOf(document));
    return source;
  }

  /**
   * The metadata copy the index holds, which exists only so kNN can pre-filter on it. Values
   * returned to clients come from PostgreSQL, so this copy is free to be normalised: a flattened
   * field matches terms exactly, while the API treats metadata filters as case-insensitive, so
   * values are lower-cased on both sides of the comparison.
   */
  private static Map<String, String> metadataOf(Document document) {
    Map<String, String> metadata = document.getMetadata();
    if (metadata == null || metadata.isEmpty()) {
      return Map.of();
    }
    Map<String, String> normalized = new HashMap<>();
    metadata.forEach((key, value) -> normalized.put(key, filterValueOf(value)));
    return normalized;
  }

  private static String filterValueOf(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
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
    double cosine = dot / (Math.sqrt(normA) * Math.sqrt(normB));
    // Clamped to [0,1] to match the Elasticsearch script above. A negative
    // cosine means the vectors share no direction, which is a non-match rather
    // than a score worse than "unrelated".
    return Math.max(0.0, cosine);
  }
}
