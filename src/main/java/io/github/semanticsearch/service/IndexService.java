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
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.transport.endpoints.BooleanResponse;

/**
 * The vector index, behind one interface whether it is Elasticsearch or the in-process map that
 * {@code elasticsearch.stub-enabled} selects by default.
 */
@Service
public class IndexService {

  private static final Logger log = LoggerFactory.getLogger(IndexService.class);

  private final ElasticsearchClient elasticsearchClient;
  private final EmbeddingService embeddingService;
  private final DocumentRepository documentRepository;
  private final LexicalIndex lexicalIndex;
  private final Chunker chunker;

  @Value("${elasticsearch.index.name:semantic-search}")
  private String indexName;

  @Value("${elasticsearch.stub-enabled:false}")
  private boolean stubEnabled;

  /**
   * How many passages to pull per requested document before collapsing to documents. A document
   * contributes as many passages as it was split into, so fetching exactly the requested number
   * would answer with fewer documents than were asked for whenever the best passages cluster in a
   * few of them.
   */
  private static final int CHUNK_OVERFETCH = 4;

  /**
   * Ceiling on passages read back per document when scoring a known set. A document longer than
   * this many passages is scored on its first {@code PASSAGE_FETCH_CEILING}, which is a bound worth
   * having: without one, a single call can pull an unbounded number of vectors.
   */
  private static final int PASSAGE_FETCH_CEILING = 32;

  /**
   * The in-memory index, keyed by passage id exactly as Elasticsearch is. Storing the document id
   * alongside each vector keeps lookups direct rather than scanning for a reverse mapping.
   */
  private final ConcurrentMap<String, StubVector> stubVectors = new ConcurrentHashMap<>();

  private record StubVector(UUID documentId, int ordinal, List<Double> vector) {}

  public IndexService(
      ElasticsearchClient elasticsearchClient,
      EmbeddingService embeddingService,
      DocumentRepository documentRepository,
      LexicalIndex lexicalIndex,
      Chunker chunker) {
    this.elasticsearchClient = elasticsearchClient;
    this.embeddingService = embeddingService;
    this.documentRepository = documentRepository;
    this.lexicalIndex = lexicalIndex;
    this.chunker = chunker;
  }

  /** Creates the index and its {@code dense_vector} mapping if the cluster does not have it. */
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
                                            .properties("ordinal", p -> p.integer(i -> i))
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
    lexicalIndex.invalidate();

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
   * <p>The document is split into passages and each is written under an id derived from the
   * document id and its position, so indexing the same document twice overwrites passage by passage
   * instead of accumulating duplicates that would each match a query separately. That is what lets
   * a failed or interrupted write simply be retried.
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
      List<Chunker.Chunk> chunks = chunker.chunk(document);
      for (Chunker.Chunk chunk : chunks) {
        List<Double> embedding = embeddingService.embed(chunk.text());
        if (embedding.isEmpty()) {
          log.error(
              "Failed to generate embedding for document {} passage {}",
              document.getId(),
              chunk.ordinal());
          return document;
        }
        String passageId = passageIdOf(document.getId(), chunk.ordinal());
        IndexResponse response =
            elasticsearchClient.index(
                i ->
                    i.index(indexName)
                        .id(passageId)
                        .document(sourceOf(document, chunk.ordinal(), embedding)));
        log.debug("Indexed {}, result: {}", passageId, response.result());
      }
      // An edit can shorten a document. Writing the new passages is an upsert on
      // a deterministic id, so the document is never unsearchable, but the tail
      // the old version left behind would keep matching queries under its id.
      deletePassages(document.getId(), chunks.size(), document.getPassageCount());

      log.info(
          "Document indexed in Elasticsearch: {} in {} passages", document.getId(), chunks.size());
      document.setVectorId(vectorIdOf(document));
      document.setPassageCount(chunks.size());
      document.setIndexed(true);
      return documentRepository.save(document);
    } catch (IOException e) {
      log.error("Failed to index document: {}", document.getId(), e);
      throw new RuntimeException("Failed to index document", e);
    }
  }

  /**
   * Deletes passages {@code from} up to {@code until}, by id.
   *
   * <p>By id and not by query, because a query reads the index and a passage written moments ago is
   * invisible until the next refresh. The ids come from the document id and counts the caller
   * already holds, so this is exact whatever the index has got around to.
   */
  private void deletePassages(UUID documentId, int from, int until) throws IOException {
    for (int ordinal = from; ordinal < until; ordinal++) {
      String passageId = passageIdOf(documentId, ordinal);
      elasticsearchClient.delete(d -> d.index(indexName).id(passageId));
    }
  }

  /**
   * Re-index a document whose content changed.
   *
   * <p>{@link #indexDocument} upserts each passage on its own deterministic id and then removes
   * whatever tail a shorter version leaves, so there is no delete-then-create step. That order
   * matters: deleting first would leave the document unsearchable between the two calls and orphan
   * its passages if the second one failed.
   *
   * @param document Document to update
   * @return Updated document
   */
  @Transactional
  public Document updateDocumentIndex(Document document) {
    // The document's terms have changed, and with them the document frequency of
    // every term it holds or used to hold.
    lexicalIndex.invalidate();
    return indexDocument(document);
  }

  /**
   * Remove every passage belonging to a document.
   *
   * <p>Deleting one entry was enough when a document was one vector. It is not now: a document
   * split into four passages would keep matching queries through the three left behind, under an id
   * whose row no longer exists.
   *
   * @return true unless the index rejected the request
   */
  public boolean deleteDocumentVectors(Document document) {
    UUID documentId = document.getId();
    if (stubEnabled) {
      stubVectors.values().removeIf(stored -> stored.documentId().equals(documentId));
      log.info("Stub passages deleted for document {}", documentId);
      return true;
    }
    try {
      // A row written before passage counts were recorded reads as zero, and its
      // single passage is at ordinal 0, so the floor of one covers it.
      deletePassages(documentId, 0, Math.max(1, document.getPassageCount()));
      log.info("Passages deleted for document {}", documentId);
      return true;
    } catch (IOException e) {
      log.error("Failed to delete passages for document {}", documentId, e);
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
    int passages = Math.min(10_000, size * CHUNK_OVERFETCH);
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
                                .k(passages)
                                .numCandidates(candidatePoolFor(passages))
                                .filter(preFilters))
                    .size(passages);
                // A threshold of zero excludes nothing, and passing it through would
                // still drop anti-correlated documents once converted, so it is left
                // unset rather than translated.
                if (minScore > 0.0) {
                  s.minScore(elasticsearchScoreOf(minScore));
                }
                return s;
              },
              ObjectNode.class);

      Map<UUID, Double> best = new LinkedHashMap<>();
      for (Hit<ObjectNode> hit : response.hits().hits()) {
        ObjectNode source = hit.source();
        if (source != null && source.hasNonNull("document_id")) {
          UUID documentId = UUID.fromString(source.get("document_id").asText());
          double score = hit.score() == null ? 0.0 : cosineOf(hit.score());
          best.merge(documentId, score, Math::max);
        }
      }
      return topDocuments(best, size);
    } catch (IOException e) {
      log.error("Failed to find similar documents", e);
      return Collections.emptyList();
    }
  }

  /**
   * Cosine similarity between a query vector and specific documents, on the same scale {@link
   * #findSimilarDocuments} reports.
   *
   * <p>Needed because hybrid retrieval unions two candidate lists. A document the lexical side
   * found but the kNN search did not has no similarity attached to it, and leaving it at zero would
   * let a term match alone decide the final score for exactly the documents the vector stage was
   * least sure about.
   *
   * @return a score per document that is present in the index; ids it does not hold are absent
   */
  public Map<UUID, Double> similarityTo(List<Double> queryVector, Collection<UUID> documentIds) {
    if (documentIds == null || documentIds.isEmpty() || queryVector.isEmpty()) {
      return Map.of();
    }
    if (stubEnabled) {
      Map<UUID, Double> scores = new HashMap<>();
      Set<UUID> wanted = Set.copyOf(documentIds);
      stubVectors.forEach(
          (passageId, stored) -> {
            if (wanted.contains(stored.documentId())) {
              scores.merge(
                  stored.documentId(), cosineSimilarity(queryVector, stored.vector()), Math::max);
            }
          });
      return scores;
    }

    // Fetched by document id, not by passage id: the caller knows which documents
    // it wants and not how many passages each was split into.
    List<FieldValue> ids =
        documentIds.stream().map(id -> FieldValue.of(id.toString())).distinct().toList();
    try {
      SearchResponse<ObjectNode> response =
          elasticsearchClient.search(
              s ->
                  s.index(indexName)
                      .query(q -> q.terms(t -> t.field("document_id").terms(v -> v.value(ids))))
                      .size(Math.min(10_000, ids.size() * PASSAGE_FETCH_CEILING)),
              ObjectNode.class);
      Map<UUID, Double> scores = new HashMap<>();
      for (Hit<ObjectNode> hit : response.hits().hits()) {
        ObjectNode source = hit.source();
        if (source == null || !source.hasNonNull("document_id") || !source.has("vector")) {
          continue;
        }
        List<Double> stored = new ArrayList<>();
        source.get("vector").forEach(value -> stored.add(value.asDouble()));
        scores.merge(
            UUID.fromString(source.get("document_id").asText()),
            cosineSimilarity(queryVector, stored),
            Math::max);
      }
      return scores;
    } catch (IOException e) {
      log.error("Failed to read vectors for {} documents", ids.size(), e);
      return Map.of();
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
   * thresholds it accepts, on the raw cosine scale the in-memory index uses. Without them the same
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
    List<Chunker.Chunk> chunks = chunker.chunk(document);
    for (Chunker.Chunk chunk : chunks) {
      List<Double> embedding = embeddingService.embed(chunk.text());
      if (embedding.isEmpty()) {
        log.error(
            "Failed to generate embedding for document {} passage {}",
            document.getId(),
            chunk.ordinal());
        return document;
      }
      stubVectors.put(
          passageIdOf(document.getId(), chunk.ordinal()),
          new StubVector(document.getId(), chunk.ordinal(), embedding));
    }
    // Same reason as the Elasticsearch path: a shorter edit leaves a tail.
    stubVectors
        .values()
        .removeIf(
            stored ->
                stored.documentId().equals(document.getId()) && stored.ordinal() >= chunks.size());

    document.setVectorId(vectorIdOf(document));
    document.setPassageCount(chunks.size());
    document.setIndexed(true);
    return documentRepository.save(document);
  }

  private List<Map.Entry<UUID, Double>> findSimilarInStub(
      List<Double> queryVector, int limit, double minScore) {
    Map<UUID, Double> best = new LinkedHashMap<>();
    stubVectors.forEach(
        (passageId, stored) -> {
          double score = cosineSimilarity(queryVector, stored.vector());
          if (score >= minScore) {
            best.merge(stored.documentId(), score, Math::max);
          }
        });
    return topDocuments(best, Math.max(1, limit));
  }

  /**
   * Collapses passage scores to documents, keeping each document's best passage.
   *
   * <p>Summing would rank a long document above a better short one for holding more passages that
   * mention the query at all, which is length bias with extra steps.
   */
  private static List<Map.Entry<UUID, Double>> topDocuments(Map<UUID, Double> best, int limit) {
    List<Map.Entry<UUID, Double>> ranked = new ArrayList<>();
    best.forEach(
        (documentId, score) -> ranked.add(new AbstractMap.SimpleEntry<>(documentId, score)));
    ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
    return ranked.size() <= limit ? List.copyOf(ranked) : List.copyOf(ranked.subList(0, limit));
  }

  /**
   * The search index uses the document id as its own document id, so writes are upserts and the two
   * stores never need a separate mapping table to stay in step.
   */
  private static String vectorIdOf(Document document) {
    return document.getId().toString();
  }

  /**
   * A passage's id in the search index. Derived from the document id and the passage's position, so
   * re-indexing overwrites passage by passage instead of accumulating duplicates that would each
   * match a query separately.
   */
  private static String passageIdOf(UUID documentId, int ordinal) {
    return documentId + "#" + ordinal;
  }

  /**
   * The indexed document body, built from plain collections.
   *
   * <p>Values must not be wrapped in {@code JsonData}. The transport serialises this map with its
   * Jackson mapper, which sees a JsonData as an opaque bean with no properties and writes {@code
   * {}}, so the vector arrives as an empty object and Elasticsearch rejects the write while parsing
   * dense_vector, which expects an array of numbers. A {@code List<Double>} and a {@code
   * Map<String, String>} serialise to the array and object the mapping declares.
   */
  static Map<String, Object> sourceOf(Document document, int ordinal, List<Double> embedding) {
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("vector", embedding);
    source.put("document_id", document.getId().toString());
    source.put("ordinal", ordinal);
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
    // Clamped to [0,1] to match the conversion applied to Elasticsearch kNN
    // scores above. A negative cosine means the vectors share no direction,
    // which is a non-match and not a score below "unrelated".
    return Math.max(0.0, cosine);
  }
}
