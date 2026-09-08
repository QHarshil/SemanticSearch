package io.github.semanticsearch.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Reciprocal rank, NDCG@k and Recall@k over a ranked list of ids.
 *
 * <p>Relevance is binary, which is what both evaluation sets here provide. Graded relevance would
 * change the gain term in {@link #ndcgAt} and nothing else.
 *
 * <p>Shared by the curated gold set and the BEIR benchmark. A second implementation would produce
 * numbers that cannot be compared with these.
 */
public final class RankingMetrics {

  private RankingMetrics() {}

  /**
   * The reciprocal of the position of the first relevant result, or zero if none appears.
   *
   * <p>Reads the whole list, not the first k. Averaged over queries this is MRR.
   */
  public static <T> double reciprocalRank(List<T> ranked, Collection<T> relevant) {
    Set<T> gold = Set.copyOf(relevant);
    for (int i = 0; i < ranked.size(); i++) {
      if (gold.contains(ranked.get(i))) {
        return 1.0 / (i + 1);
      }
    }
    return 0.0;
  }

  /**
   * Discounted cumulative gain over the first k results, divided by the best arrangement of the
   * same relevant documents.
   *
   * <p>The ideal ranking puts every relevant document at the top, so the denominator sums the same
   * discount over {@code min(relevant, k)} positions. Dividing by a fixed k instead would score a
   * query with one relevant document out of a possible ten as though it had failed to find nine
   * that do not exist.
   */
  public static <T> double ndcgAt(List<T> ranked, Collection<T> relevant, int k) {
    Set<T> gold = Set.copyOf(relevant);
    if (gold.isEmpty() || k <= 0) {
      return 0.0;
    }

    double gain = 0.0;
    for (int i = 0; i < Math.min(ranked.size(), k); i++) {
      if (gold.contains(ranked.get(i))) {
        gain += 1.0 / log2(i + 2);
      }
    }

    double ideal = 0.0;
    for (int i = 0; i < Math.min(gold.size(), k); i++) {
      ideal += 1.0 / log2(i + 2);
    }
    return ideal == 0.0 ? 0.0 : gain / ideal;
  }

  /** The share of relevant documents that appear in the first k results. */
  public static <T> double recallAt(List<T> ranked, Collection<T> relevant, int k) {
    Set<T> gold = Set.copyOf(relevant);
    if (gold.isEmpty()) {
      return 0.0;
    }
    long found = ranked.stream().limit(Math.max(0, k)).filter(gold::contains).count();
    return (double) found / gold.size();
  }

  private static double log2(int value) {
    return Math.log(value) / Math.log(2);
  }
}
