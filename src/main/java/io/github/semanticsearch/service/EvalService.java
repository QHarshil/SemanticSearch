package io.github.semanticsearch.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import io.github.semanticsearch.model.SearchRequest;
import io.github.semanticsearch.model.SearchResult;
import io.github.semanticsearch.repository.DocumentRepository;

/** Scores a gold set of query and document pairs, reporting MRR, NDCG@k and Recall@k. */
@Service
public class EvalService {

  private final SearchService searchService;
  private final DocumentRepository documentRepository;

  public EvalService(SearchService searchService, DocumentRepository documentRepository) {
    this.searchService = searchService;
    this.documentRepository = documentRepository;
  }

  public EvalResult runEval(List<EvalQuery> queries, int k) {
    if (queries == null || queries.isEmpty()) {
      return new EvalResult(0, 0.0, 0.0, 0.0, Collections.emptyList());
    }

    List<QueryEval> perQuery = new ArrayList<>();
    double mrrSum = 0.0;
    double ndcgSum = 0.0;
    double recallSum = 0.0;

    for (EvalQuery q : queries) {
      SearchRequest request =
          SearchRequest.builder()
              .query(q.query())
              .limit(k)
              .minScore(0.0)
              .includeContent(false)
              .includeHighlights(false)
              .build();

      List<SearchResult> results = searchService.search(request);
      List<UUID> hits = results.stream().map(SearchResult::getId).collect(Collectors.toList());

      double rr = RankingMetrics.reciprocalRank(hits, q.relevantDocumentIds());
      double ndcg = RankingMetrics.ndcgAt(hits, q.relevantDocumentIds(), k);
      double recall = RankingMetrics.recallAt(hits, q.relevantDocumentIds(), k);
      mrrSum += rr;
      ndcgSum += ndcg;
      recallSum += recall;

      perQuery.add(new QueryEval(q.query(), rr, ndcg, recall));
    }

    double mrr = mrrSum / queries.size();
    double ndcg = ndcgSum / queries.size();
    double recall = recallSum / queries.size();
    return new EvalResult(queries.size(), mrr, ndcg, recall, perQuery);
  }

  /**
   * Runs the built-in gold set against the seeded demo corpus.
   *
   * <p>Queries are phrased the way someone would search, not copied from the document text, so a
   * result depends on graded similarity and not on an exact string match.
   *
   * <p>Every gold document genuinely covers its query's topic. A pair whose answer is not in the
   * corpus, or whose gold document is only loosely related, can be satisfied only by accident and
   * drags the reported score down for reasons unrelated to ranking quality.
   */
  public EvalResult runCuratedEval(int k) {
    List<EvalQuery> curated =
        List.of(
            new EvalQuery("how does embedding similarity work", lookup("Vector Search Basics")),
            new EvalQuery("what affects result ordering", lookup("Ranking Signals")),
            new EvalQuery("keeping p95 response time low", lookup("Latency Budgets")),
            new EvalQuery("boosting newer documents", lookup("Recency and Freshness")),
            new EvalQuery("measuring search quality offline", lookup("Evaluating Relevance")),
            new EvalQuery("term frequency scoring", lookup("Inverted Indexes and BM25")),
            new EvalQuery("splitting large files into passages", lookup("Chunking Long Documents")),
            new EvalQuery("avoiding repeated work per query", lookup("Caching Query Results")));
    return runEval(curated, k);
  }

  public record EvalQuery(String query, List<UUID> relevantDocumentIds) {}

  public record QueryEval(String query, double rr, double ndcg, double recall) {}

  public record EvalResult(
      int totalQueries, double mrr, double ndcg, double recallAtK, List<QueryEval> details) {}

  /**
   * Resolves a gold document by title, failing if it is absent.
   *
   * <p>Failing loudly matters here: returning an empty list instead would score every metric for
   * that query as 0.0, making a broken or unseeded gold set indistinguishable from genuinely poor
   * ranking. The harness would report those zeros as though they were a measurement.
   */
  private List<UUID> lookup(String title) {
    return documentRepository
        .findByTitle(title)
        .map(d -> List.of(d.getId()))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Gold set references a document that is not in the corpus: '"
                        + title
                        + "'. Seed the demo documents first (POST /api/v1/documents/seed)."));
  }
}
