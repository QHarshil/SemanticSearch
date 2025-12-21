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

  private final EmbeddingService embeddingService;
  private final IndexService indexService;
  private final DocumentRepository documentRepository;
  private final SearchProperties searchProperties;

  public SearchService(
      EmbeddingService embeddingService,
      IndexService indexService,
      DocumentRepository documentRepository,
      SearchProperties searchProperties) {
    this.embeddingService = embeddingService;
    this.indexService = indexService;
    this.documentRepository = documentRepository;
    this.searchProperties = searchProperties;
  }

  /**
   * Perform semantic search based on query text. Caches results for frequent queries to improve
   * performance.
   *
   * @param request Search request containing query and parameters
   * @return List of search results
   */
  @Cacheable(value = "searchResults", key = "#request.toString()", unless = "#result.isEmpty()")
  public List<SearchResult> search(SearchRequest request) {
    log.debug("Performing semantic search for query: {}", request.getQuery());

    // Generate embedding for query
    List<Double> queryVector = embeddingService.embed(request.getQuery());
    if (queryVector.isEmpty()) {
      log.warn("Failed to generate embedding for query: {}", request.getQuery());
      return Collections.emptyList();
    }

    // Find similar documents
    int limit = Math.max(1, request.getLimit());
    double minScore = Math.max(0.0, request.getMinScore());
    List<Map.Entry<UUID, Double>> similarDocuments =
        indexService.findSimilarDocuments(queryVector, limit, minScore);

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
    List<Double> documentVector = embeddingService.embed(document.getContent());
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

    Map<String, String> metadata = document.getMetadata() == null ? Map.of() : document.getMetadata();
    for (Map.Entry<String, String> filter : filters.entrySet()) {
      String value = metadata.get(filter.getKey());
      if (value == null || !value.equalsIgnoreCase(filter.getValue())) {
        return false;
      }
    }
    return true;
  }

  private Map<String, String> projectMetadata(Document document, List<String> fields) {
    Map<String, String> metadata = document.getMetadata() == null ? Map.of() : document.getMetadata();
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

  private Map<UUID, Double> computeLexicalScores(String query, Map<UUID, Document> documents) {
    List<String> queryTerms = tokenizeList(query);
    Map<String, Integer> queryFreq = termFreq(queryTerms);
    int docCount = documents.size();
    double avgLen =
        documents.values().stream()
            .mapToInt(doc -> tokenizeList(doc.getContent()).size())
            .average()
            .orElse(1.0);

    Map<String, Integer> docFreq = new HashMap<>();
    documents
        .values()
        .forEach(
            doc -> {
              Set<String> terms = new HashSet<>(tokenizeList(doc.getContent()));
              for (String t : terms) {
                docFreq.merge(t, 1, Integer::sum);
              }
            });

    Map<UUID, Double> scores = new HashMap<>();
    for (Map.Entry<UUID, Document> entry : documents.entrySet()) {
      List<String> docTerms = tokenizeList(entry.getValue().getContent());
      Map<String, Integer> tf = termFreq(docTerms);
      double docLen = docTerms.size();
      double bm25 = 0.0;
      for (String term : queryFreq.keySet()) {
        int df = docFreq.getOrDefault(term, 0);
        if (df == 0) {
          continue;
        }
        double idf =
            Math.log((docCount - df + 0.5) / (df + 0.5) + 1.0); // smooth idf
        double freq = tf.getOrDefault(term, 0);
        double k1 = searchProperties.getBm25K1();
        double b = searchProperties.getBm25B();
        double denom = freq + k1 * (1 - b + b * (docLen / avgLen));
        bm25 += idf * ((freq * (k1 + 1)) / (denom == 0 ? 1 : denom));
      }
      scores.put(entry.getKey(), bm25 == 0.0 ? 0.0 : ScoreCalculator.clamp(bm25 / (bm25 + 1)));
    }
    return scores;
  }

  private Set<String> tokenize(String text) {
    if (text == null || text.isBlank()) {
      return Set.of();
    }
    String[] parts = text.toLowerCase().split("[^a-z0-9]+");
    return Arrays.stream(parts).filter(p -> !p.isBlank()).collect(Collectors.toSet());
  }

  private List<String> tokenizeList(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    String[] parts = text.toLowerCase().split("[^a-z0-9]+");
    return Arrays.stream(parts).filter(p -> !p.isBlank()).toList();
  }

  private Map<String, Integer> termFreq(List<String> terms) {
    Map<String, Integer> tf = new HashMap<>();
    for (String t : terms) {
      tf.merge(t, 1, Integer::sum);
    }
    return tf;
  }

}
