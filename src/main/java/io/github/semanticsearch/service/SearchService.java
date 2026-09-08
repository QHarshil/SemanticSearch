package io.github.semanticsearch.service;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import io.github.semanticsearch.config.FusionMethod;
import io.github.semanticsearch.config.SearchProperties;
import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.model.SearchRequest;
import io.github.semanticsearch.model.SearchResult;
import io.github.semanticsearch.repository.DocumentRepository;
import io.github.semanticsearch.util.ScoreCalculator;

/**
 * The query path: embed, retrieve twice, fuse, re-score, truncate.
 *
 * <p>Every score this returns is in {@code [0,1]}, whichever fusion method produced it, because
 * {@code minScore} is a caller-facing contract and has to mean one thing.
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
  private final LexicalIndex lexicalIndex;
  private final SearchMetrics metrics;

  public SearchService(
      EmbeddingService embeddingService,
      IndexService indexService,
      DocumentRepository documentRepository,
      SearchProperties searchProperties,
      LexicalIndex lexicalIndex,
      SearchMetrics metrics) {
    this.embeddingService = embeddingService;
    this.indexService = indexService;
    this.documentRepository = documentRepository;
    this.searchProperties = searchProperties;
    this.lexicalIndex = lexicalIndex;
    this.metrics = metrics;
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
    log.debug("Performing search for query: {}", request.getQuery());

    List<Double> queryVector =
        metrics.time(SearchMetrics.Stage.EMBED, () -> embeddingService.embed(request.getQuery()));
    if (queryVector.isEmpty()) {
      log.warn("Failed to generate embedding for query: {}", request.getQuery());
      return Collections.emptyList();
    }

    int limit = Math.max(1, request.getLimit());
    double minScore = Math.max(0.0, request.getMinScore());
    boolean hybrid = searchProperties.isHybridEnabled();

    // Over-fetch from each retriever, then fuse and truncate. Retrieving exactly
    // `limit` from either side would leave fusion nothing to do: whichever
    // document each retriever ranked last would still be in the answer.
    int candidateLimit = Math.min(limit * CANDIDATE_MULTIPLIER, MAX_CANDIDATES);

    // Retrieval is deliberately unthresholded. minScore is a floor on the score a
    // caller receives, and the score a caller receives is the fused one computed
    // below. Passing minScore to the vector stage would apply it to a different,
    // always-lower number and drop documents whose final score clears the bar.
    //
    // Filters do go into the vector retrieval, so the candidate pool is filled
    // with documents that can actually be returned. The post-filter below still
    // runs, because neither the in-memory index nor the lexical index pre-filters
    // and both have to honour the same contract.
    List<Map.Entry<UUID, Double>> vectorHits =
        metrics.time(
            SearchMetrics.Stage.VECTOR_RETRIEVAL,
            () ->
                indexService.findSimilarDocuments(
                    queryVector, candidateLimit, 0.0, request.getFilters()));
    // The two retrievers run over the whole corpus independently. Scoring only
    // what the vector stage returned would make lexical matching a tiebreaker: a
    // document whose terms match the query exactly but whose embedding sits
    // outside the vector neighbourhood could not be recovered at any weight.
    List<Map.Entry<UUID, Double>> lexicalHits =
        hybrid
            ? metrics.time(
                SearchMetrics.Stage.LEXICAL_RETRIEVAL,
                () -> lexicalIndex.search(request.getQuery(), candidateLimit))
            : List.<Map.Entry<UUID, Double>>of();

    Set<UUID> candidates = new LinkedHashSet<>();
    vectorHits.forEach(hit -> candidates.add(hit.getKey()));
    lexicalHits.forEach(hit -> candidates.add(hit.getKey()));
    if (candidates.isEmpty()) {
      log.debug("No candidates found for query: {}", request.getQuery());
      return Collections.emptyList();
    }

    Map<UUID, Document> documentsMap =
        metrics.time(
            SearchMetrics.Stage.HYDRATE,
            () ->
                documentRepository.findAllById(candidates).stream()
                    .collect(Collectors.toMap(Document::getId, doc -> doc)));

    Map<UUID, Double> vectorScores = scoresOf(vectorHits);
    // A document only the lexical side found still has a vector, and its true
    // similarity is what the blend needs. Leaving it at zero would let a term
    // match alone decide the score for exactly the documents kNN was least sure
    // about. Hydration runs first because the index reads passages by id, and the
    // row is what says how many the document has.
    List<Document> unscored =
        candidates.stream()
            .filter(id -> !vectorScores.containsKey(id))
            .map(documentsMap::get)
            .filter(Objects::nonNull)
            .toList();
    vectorScores.putAll(indexService.similarityTo(queryVector, unscored));

    Map<UUID, Double> lexicalScores =
        hybrid ? lexicalIndex.score(request.getQuery(), candidates) : Map.of();
    Map<UUID, Integer> vectorRanks = ranksOf(vectorHits);
    Map<UUID, Integer> lexicalRanks = ranksOf(lexicalHits);

    FusionMethod fusion = FusionMethod.from(searchProperties.getFusion());
    List<SearchResult> results = new ArrayList<>();
    for (UUID documentId : candidates) {
      Document document = documentsMap.get(documentId);
      if (document == null || !matchesFilters(document, request.getFilters())) {
        continue;
      }

      double fused =
          switch (fusion) {
            case BLEND -> blended(documentId, vectorScores, lexicalScores);
            case RRF ->
                ScoreCalculator.reciprocalRankFusion(
                    vectorRanks.get(documentId), lexicalRanks.get(documentId), searchProperties);
          };
      double boosted =
          ScoreCalculator.applyMetadataBoosts(
              document, fused, searchProperties.getMetadataBoosts());
      double withRecency = ScoreCalculator.applyRecency(document, boosted, searchProperties);

      // Applied here, against the score that will be reported, so a result can
      // never come back scoring below the threshold the caller asked for.
      if (withRecency < minScore) {
        continue;
      }

      results.add(
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
              .build());
    }

    // Candidates arrive in vector-score order followed by whatever the lexical
    // side added, and fusion, boosts and recency all change the score. Skipping
    // this sort would return an order reflecting vector similarity alone, making
    // every one of those signals inert, including in the eval metrics, which
    // depend solely on rank position.
    results.sort(Comparator.comparingDouble(SearchResult::getScore).reversed());

    // The wider candidate pool only pays off if the final top-N is chosen on the
    // fused score, so truncation runs after fusion.
    if (results.size() > limit) {
      results = new ArrayList<>(results.subList(0, limit));
    }

    metrics.recordResults(results.size());
    log.debug("Found {} results for query: {}", results.size(), request.getQuery());
    return results;
  }

  /**
   * The weighted blend of the two signals, floored at the vector score so BM25 can raise a document
   * and never lower one.
   *
   * <p>Under a semantic embedder, matching on meaning without sharing a word is the expected case,
   * so a document holding none of the query's terms must not be scored as a lexical miss. Handing
   * it the vector score in place of a missing lexical one does that, and creates a second problem.
   * A document holding one common query term then scores below a document holding none of them,
   * because a low BM25 value drags the blend down while an absent one cannot. The floor removes
   * that step. On SciFact it changes nothing, NDCG@10 0.6732 with the floor and without it.
   */
  private double blended(
      UUID documentId, Map<UUID, Double> vectorScores, Map<UUID, Double> lexicalScores) {
    double vectorScore = ScoreCalculator.clamp(vectorScores.getOrDefault(documentId, 0.0));
    double lexicalScore = lexicalScores.getOrDefault(documentId, 0.0);
    return Math.max(
        vectorScore, ScoreCalculator.blendScores(vectorScore, lexicalScore, searchProperties));
  }

  private static Map<UUID, Double> scoresOf(List<Map.Entry<UUID, Double>> hits) {
    Map<UUID, Double> scores = new HashMap<>();
    hits.forEach(hit -> scores.putIfAbsent(hit.getKey(), hit.getValue()));
    return scores;
  }

  /** Positions in a ranked list, 1-based, which is the form reciprocal rank fusion expects. */
  private static Map<UUID, Integer> ranksOf(List<Map.Entry<UUID, Double>> hits) {
    Map<UUID, Integer> ranks = new HashMap<>();
    for (int i = 0; i < hits.size(); i++) {
      ranks.putIfAbsent(hits.get(i).getKey(), i + 1);
    }
    return ranks;
  }

  /**
   * Documents nearest to an existing one, excluding itself.
   *
   * <p>The whole document is embedded as the query. Indexing splits it into passages and this does
   * not, so under a provider with a context window a long document is compared on its opening
   * alone. Averaging its passage vectors would use all of it and is the obvious improvement; it
   * needs the index to hand back vectors, which nothing else asks for yet.
   *
   * @param documentId the document to find neighbours for
   * @param limit how many neighbours to return
   * @param minScore floor on the similarity a neighbour must reach
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

    List<Map.Entry<UUID, Double>> similarDocuments =
        indexService.findSimilarDocuments(documentVector, limit + 1, minScore);

    similarDocuments =
        similarDocuments.stream()
            .filter(entry -> !entry.getKey().equals(documentId))
            .limit(limit)
            .collect(Collectors.toList());

    if (similarDocuments.isEmpty()) {
      return Collections.emptyList();
    }

    List<UUID> documentIds =
        similarDocuments.stream().map(Map.Entry::getKey).collect(Collectors.toList());

    Map<UUID, Document> documentsMap =
        documentRepository.findAllById(documentIds).stream()
            .collect(Collectors.toMap(Document::getId, doc -> doc));

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
                .score(entry.getValue())
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
}
