package io.github.semanticsearch.service;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import io.github.semanticsearch.config.SearchProperties;
import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.model.SearchRequest;
import io.github.semanticsearch.model.SearchResult;
import io.github.semanticsearch.repository.DocumentRepository;
import io.github.semanticsearch.util.ScoreCalculator;

/**
 * Service for semantic search functionality. Coordinates embedding generation, vector search, and
 * result processing.
 */
@Service
public class SearchService {

  private static final Logger log = LoggerFactory.getLogger(SearchService.class);

  /** How many vector candidates to fetch per requested result before re-ranking. */
  private static final int CANDIDATE_MULTIPLIER = 5;

  /** Ceiling on the candidate pool, so a large limit cannot pull the whole index. */
  private static final int MAX_CANDIDATES = 200;

  private final EmbeddingService embeddingService;
  private final IndexService indexService;
  private final DocumentRepository documentRepository;
  private final SearchProperties searchProperties;
  private final CorpusStatistics corpusStatistics;

  public SearchService(
      EmbeddingService embeddingService,
      IndexService indexService,
      DocumentRepository documentRepository,
      SearchProperties searchProperties,
      CorpusStatistics corpusStatistics) {
    this.embeddingService = embeddingService;
    this.indexService = indexService;
    this.documentRepository = documentRepository;
    this.searchProperties = searchProperties;
    this.corpusStatistics = corpusStatistics;
  }

  /**
   * Perform semantic search based on query text. Caches results for frequent queries to improve
   * performance.
   *
   * @param request Search request containing query and parameters
   * @return List of search results
   */
  // Keyed on the request object itself, which has value-based equals/hashCode over
  // every field that changes the result. A key derived from identity, such as the
  // default toString of a class that does not override it, would make each call a
  // fresh entry: the cache would never hit and would grow without bound.
  @Cacheable(value = "searchResults", key = "#request", unless = "#result.isEmpty()")
  public List<SearchResult> search(SearchRequest request) {
    log.debug("Performing semantic search for query: {}", request.getQuery());

    // Generate embedding for query
    List<Double> queryVector = embeddingService.embed(request.getQuery());
    if (queryVector.isEmpty()) {
      log.warn("Failed to generate embedding for query: {}", request.getQuery());
      return Collections.emptyList();
    }

    int limit = Math.max(1, request.getLimit());
    double minScore = Math.max(0.0, request.getMinScore());

    // Over-fetch from the vector stage, then re-rank and truncate to limit.
    // Retrieving exactly `limit` candidates would leave the lexical stage
    // powerless: a document matching the query strongly on words but sitting just
    // outside the vector top-N could never be recovered, however high BM25 scored
    // it. The wider pool is what lets hybrid scoring change the outcome rather
    // than relabel it.
    int candidateLimit = Math.min(limit * CANDIDATE_MULTIPLIER, MAX_CANDIDATES);
    // Retrieval is deliberately unthresholded. minScore is a floor on the score a
    // caller receives, and the score a caller receives is the blended one computed
    // below - passing minScore to the vector stage would apply it to a different,
    // always-lower number and drop documents whose final score clears the bar.
    //
    // Filters do go into retrieval, so the candidate pool is filled with documents
    // that can actually be returned. The post-filter below still runs, because the
    // in-memory index does not pre-filter and has to enforce the same contract.
    List<Map.Entry<UUID, Double>> similarDocuments =
        indexService.findSimilarDocuments(queryVector, candidateLimit, 0.0, request.getFilters());

    if (similarDocuments.isEmpty()) {
      log.debug("No similar documents found for query: {}", request.getQuery());
      return Collections.emptyList();
    }

    // Retrieve document details
    List<UUID> documentIds =
        similarDocuments.stream().map(Map.Entry::getKey).collect(Collectors.toList());

    Map<UUID, Document> documentsMap =
        documentRepository.findAllById(documentIds).stream()
            .collect(Collectors.toMap(Document::getId, doc -> doc));

    Map<UUID, Double> lexicalScores = Collections.emptyMap();
    if (searchProperties.isHybridEnabled()) {
      lexicalScores = computeLexicalScores(request.getQuery(), documentsMap);
    }

    // Build search results with optional hybrid/metadata boosts
    List<SearchResult> results = new ArrayList<>();
    for (Map.Entry<UUID, Double> entry : similarDocuments) {
      UUID documentId = entry.getKey();
      Document document = documentsMap.get(documentId);

      if (document != null) {
        if (!matchesFilters(document, request.getFilters())) {
          continue;
        }

        double vectorScore = ScoreCalculator.clamp(entry.getValue());
        double lexicalScore = lexicalScores.getOrDefault(documentId, vectorScore);
        double blended = ScoreCalculator.blendScores(vectorScore, lexicalScore, searchProperties);
        double boosted =
            ScoreCalculator.applyMetadataBoosts(
                document, blended, searchProperties.getMetadataBoosts());
        double withRecency =
            ScoreCalculator.applyRecency(
                document,
                boosted,
                searchProperties.isRecencyEnabled(),
                searchProperties.getRecencyHalfLifeSeconds());

        // Applied here, against the score that will be reported, so a result can
        // never come back scoring below the threshold the caller asked for.
        if (withRecency < minScore) {
          continue;
        }

        SearchResult result =
            SearchResult.builder()
                .id(document.getId())
                .title(document.getTitle())
                .content(request.isIncludeContent() ? document.getContent() : null)
                .metadata(projectMetadata(document, request.getFields()))
                .score(withRecency)
                .highlights(
                    request.isIncludeHighlights()
                        ? generateHighlights(document.getContent(), request.getQuery())
                        : null)
                .build();

        results.add(result);
      }
    }

    // Re-sort by the final score. Results arrive in vector-score order, and the
    // blending, metadata boosts and recency decay above all change that score.
    // Skipping this sort would return an order reflecting vector similarity alone,
    // making every one of those relevance features inert - including in the eval
    // metrics, which depend solely on rank position.
    results.sort(Comparator.comparingDouble(SearchResult::getScore).reversed());

    // Truncate after re-ranking, not before: the point of the wider candidate
    // pool is that the final top-N is chosen on the blended score.
    if (results.size() > limit) {
      results = new ArrayList<>(results.subList(0, limit));
    }

    log.debug("Found {} results for query: {}", results.size(), request.getQuery());
    return results;
  }

  /**
   * Find documents similar to a given document.
   *
   * @param documentId ID of the document to find similar documents for
   * @param limit Maximum number of results to return
   * @param minScore Minimum similarity score threshold
   * @return List of search results
   */
  public List<SearchResult> findSimilarDocuments(UUID documentId, int limit, double minScore) {
    Optional<Document> documentOpt = documentRepository.findById(documentId);
    if (documentOpt.isEmpty()) {
      log.warn("Document not found: {}", documentId);
      return Collections.emptyList();
    }

    Document document = documentOpt.get();
    List<Double> documentVector = embeddingService.embed(Tokenizer.indexableText(document));
    if (documentVector.isEmpty()) {
      log.warn("Failed to generate embedding for document: {}", documentId);
      return Collections.emptyList();
    }

    // Find similar documents
    List<Map.Entry<UUID, Double>> similarDocuments =
        indexService.findSimilarDocuments(documentVector, limit + 1, minScore);

    // Remove the original document from results
    similarDocuments =
        similarDocuments.stream()
            .filter(entry -> !entry.getKey().equals(documentId))
            .limit(limit)
            .collect(Collectors.toList());

    if (similarDocuments.isEmpty()) {
      return Collections.emptyList();
    }

    // Retrieve document details
    List<UUID> documentIds =
        similarDocuments.stream().map(Map.Entry::getKey).collect(Collectors.toList());

    Map<UUID, Document> documentsMap =
        documentRepository.findAllById(documentIds).stream()
            .collect(Collectors.toMap(Document::getId, doc -> doc));

    // Build search results
    List<SearchResult> results = new ArrayList<>();
    for (Map.Entry<UUID, Double> entry : similarDocuments) {
      Document similarDoc = documentsMap.get(entry.getKey());
      if (similarDoc != null) {
        if (!matchesFilters(similarDoc, Collections.emptyMap())) {
          continue;
        }

        SearchResult result =
            SearchResult.builder()
                .id(similarDoc.getId())
                .title(similarDoc.getTitle())
                .content(similarDoc.getContent())
                .metadata(similarDoc.getMetadata())
                .score(entry.getValue()) // Use double directly without conversion
                .build();

        results.add(result);
      }
    }

    return results;
  }

  /**
   * Generate text highlights for search results. Extracts relevant snippets from content that match
   * the query.
   *
   * @param content Document content
   * @param query Search query
   * @return List of highlighted text snippets
   */
  private List<String> generateHighlights(String content, String query) {
    List<String> highlights = new ArrayList<>();
    if (content == null || content.isEmpty() || query == null || query.isEmpty()) {
      return highlights;
    }

    // Simple highlight generation by splitting content into sentences
    String[] sentences = content.split("[.!?]");
    String[] queryTerms = query.toLowerCase().split("\\s+");

    for (String sentence : sentences) {
      String sentenceLower = sentence.toLowerCase();
      boolean relevant = false;

      for (String term : queryTerms) {
        if (sentenceLower.contains(term)) {
          relevant = true;
          break;
        }
      }

      if (relevant) {
        String highlight = sentence.trim();
        if (!highlight.isEmpty()) {
          highlights.add(highlight);
        }

        // Limit number of highlights
        if (highlights.size() >= 3) {
          break;
        }
      }
    }

    return highlights;
  }

  private boolean matchesFilters(Document document, Map<String, String> filters) {
    if (filters == null || filters.isEmpty()) {
      return true;
    }

    Map<String, String> metadata =
        document.getMetadata() == null ? Map.of() : document.getMetadata();
    for (Map.Entry<String, String> filter : filters.entrySet()) {
      String value = metadata.get(filter.getKey());
      if (value == null || !value.equalsIgnoreCase(filter.getValue())) {
        return false;
      }
    }
    return true;
  }

  private Map<String, String> projectMetadata(Document document, List<String> fields) {
    Map<String, String> metadata =
        document.getMetadata() == null ? Map.of() : document.getMetadata();
    if (fields == null || fields.isEmpty()) {
      return metadata;
    }

    Map<String, String> projected = new HashMap<>();
    for (String key : fields) {
      if (metadata.containsKey(key)) {
        projected.put(key, metadata.get(key));
      }
    }
    return projected;
  }

  /**
   * BM25 over the candidate documents, using corpus-wide term statistics.
   *
   * <p>Note this re-ranks rather than retrieves: only documents the vector search already returned
   * can be scored, so a document that matches the query lexically but fell below the vector
   * minScore is unreachable. That is a property of the pipeline, not of this method.
   */
  private Map<UUID, Double> computeLexicalScores(String query, Map<UUID, Document> documents) {
    Map<String, Integer> queryFreq = termFreq(Tokenizer.tokenize(query));
    CorpusStatistics.Snapshot corpus = corpusStatistics.current();
    double k1 = searchProperties.getBm25K1();
    double b = searchProperties.getBm25B();

    Map<UUID, Double> scores = new HashMap<>();
    for (Map.Entry<UUID, Document> entry : documents.entrySet()) {
      List<String> docTerms = Tokenizer.tokenize(entry.getValue());
      Map<String, Integer> tf = termFreq(docTerms);
      double docLen = docTerms.size();
      double bm25 = 0.0;

      for (String term : queryFreq.keySet()) {
        double freq = tf.getOrDefault(term, 0);
        if (freq == 0) {
          continue;
        }
        int df = corpus.documentFrequencyOf(term);
        if (df == 0) {
          // The corpus snapshot predates this document; treat the term as rare
          // rather than skipping a match the document genuinely contains.
          df = 1;
        }
        double idf =
            Math.log((corpus.documentCount() - df + 0.5) / (df + 0.5) + 1.0); // smoothed idf
        double denom = freq + k1 * (1 - b + b * (docLen / corpus.averageLength()));
        bm25 += idf * ((freq * (k1 + 1)) / (denom == 0 ? 1 : denom));
      }
      scores.put(entry.getKey(), bm25 == 0.0 ? 0.0 : ScoreCalculator.clamp(bm25 / (bm25 + 1)));
    }
    return scores;
  }

  private Map<String, Integer> termFreq(List<String> terms) {
    Map<String, Integer> tf = new HashMap<>();
    for (String t : terms) {
      tf.merge(t, 1, Integer::sum);
    }
    return tf;
  }
}
