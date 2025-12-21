package io.github.semanticsearch.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import io.github.semanticsearch.model.SearchRequest;
import io.github.semanticsearch.model.SearchResult;
import io.github.semanticsearch.repository.DocumentRepository;

/**
 * Simple evaluation harness for semantic search. Computes MRR and NDCG@k for a small gold set
 * seeded in the database.
 */
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

      double rr = reciprocalRank(hits, q.relevantDocumentIds());
      double ndcg = ndcg(hits, q.relevantDocumentIds(), k);
      double recall = recall(hits, q.relevantDocumentIds(), k);
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

  public EvalResult runCuratedEval(int k) {
    List<EvalQuery> curated =
        List.of(
            new EvalQuery("vector search embeddings", lookup("Vector Search Basics")),
            new EvalQuery("ranking signals metadata boosts", lookup("Ranking Signals")),
            new EvalQuery("latency budget p95", lookup("Latency Budgets")),
            new EvalQuery("recency decay freshness", lookup("Latency Budgets")));
    return runEval(curated, k);
  }

  private double reciprocalRank(List<UUID> hits, List<UUID> gold) {
    for (int i = 0; i < hits.size(); i++) {
      if (gold.contains(hits.get(i))) {
        return 1.0 / (i + 1);
      }
    }
    return 0.0;
  }

  private double ndcg(List<UUID> hits, List<UUID> gold, int k) {
    double dcg = 0.0;
    for (int i = 0; i < Math.min(hits.size(), k); i++) {
      UUID docId = hits.get(i);
      int rel = gold.contains(docId) ? 1 : 0;
      if (rel > 0) {
        dcg += rel / (Math.log(i + 2) / Math.log(2));
      }
    }

    List<Integer> ideal = gold.stream().map(g -> 1).collect(Collectors.toList());
    double idcg = 0.0;
    for (int i = 0; i < Math.min(ideal.size(), k); i++) {
      int rel = ideal.get(i);
      idcg += rel / (Math.log(i + 2) / Math.log(2));
    }
    if (idcg == 0) {
      return 0.0;
    }
    return dcg / idcg;
  }

  private double recall(List<UUID> hits, List<UUID> gold, int k) {
    if (gold == null || gold.isEmpty()) {
      return 0.0;
    }
    long found = hits.stream().limit(k).filter(gold::contains).count();
    return (double) found / (double) gold.size();
  }

  public record EvalQuery(String query, List<UUID> relevantDocumentIds) {}

  public record QueryEval(String query, double rr, double ndcg, double recall) {}

  public record EvalResult(int totalQueries, double mrr, double ndcg, double recallAtK, List<QueryEval> details) {}

  private List<UUID> lookup(String title) {
    return documentRepository
        .findByTitle(title)
        .map(d -> List.of(d.getId()))
        .orElseGet(List::of);
  }
}
