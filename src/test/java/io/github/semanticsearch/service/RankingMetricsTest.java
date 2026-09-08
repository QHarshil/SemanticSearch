package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Hand-computed expectations for the three metrics.
 *
 * <p>Every expected value below is worked out in the comment beside it from the definition, not
 * read back from a run of the code. A metric checked against its own output would agree with any
 * implementation, including a wrong one, and these numbers are the ones every relevance claim in
 * this repository rests on.
 */
class RankingMetricsTest {

  private static final List<String> RANKED = List.of("a", "b", "c", "d", "e");

  @Test
  void reciprocalRankIsOneOverThePositionOfTheFirstHit() {
    assertEquals(1.0, RankingMetrics.reciprocalRank(RANKED, Set.of("a")), 1e-9);
    assertEquals(0.5, RankingMetrics.reciprocalRank(RANKED, Set.of("b")), 1e-9);
    assertEquals(0.25, RankingMetrics.reciprocalRank(RANKED, Set.of("d")), 1e-9);
    // Only the first hit counts, so a second relevant document further down
    // cannot raise the score.
    assertEquals(0.5, RankingMetrics.reciprocalRank(RANKED, Set.of("b", "e")), 1e-9);
    assertEquals(0.0, RankingMetrics.reciprocalRank(RANKED, Set.of("z")), 1e-9);
  }

  @Test
  void reciprocalRankLooksPastTheTopK() {
    // No k to pass, so a hit at position five still scores 0.2.
    assertEquals(0.2, RankingMetrics.reciprocalRank(RANKED, Set.of("e")), 1e-9);
  }

  @Test
  void ndcgIsOneWhenEveryRelevantDocumentIsAtTheTop() {
    assertEquals(1.0, RankingMetrics.ndcgAt(RANKED, Set.of("a"), 5), 1e-9);
    assertEquals(1.0, RankingMetrics.ndcgAt(RANKED, Set.of("a", "b"), 5), 1e-9);
    assertEquals(1.0, RankingMetrics.ndcgAt(RANKED, Set.of("a", "b", "c"), 5), 1e-9);
  }

  @Test
  void ndcgDiscountsByThePositionOfEachHit() {
    // One relevant document at rank 2: gain 1/log2(3) = 0.6309, ideal 1/log2(2) = 1.
    assertEquals(0.63093, RankingMetrics.ndcgAt(RANKED, Set.of("b"), 5), 1e-5);
    // Rank 5: gain 1/log2(6) = 0.38685.
    assertEquals(0.38685, RankingMetrics.ndcgAt(RANKED, Set.of("e"), 5), 1e-5);
  }

  @Test
  void ndcgNormalisesAgainstTheRelevantDocumentsThatExistNotAgainstK() {
    // Relevant at ranks 1 and 3: gain 1 + 1/log2(4) = 1.5.
    // Ideal for two relevant documents: 1 + 1/log2(3) = 1.63093.
    assertEquals(1.5 / 1.63093, RankingMetrics.ndcgAt(RANKED, Set.of("a", "c"), 5), 1e-5);
  }

  @Test
  void ndcgIgnoresHitsPastK() {
    // "d" sits at rank 4, so at k=3 it contributes nothing, while the ideal is
    // still that of a single relevant document.
    assertEquals(0.0, RankingMetrics.ndcgAt(RANKED, Set.of("d"), 3), 1e-9);
  }

  @Test
  void ndcgIsZeroWithNothingRelevant() {
    assertEquals(0.0, RankingMetrics.ndcgAt(RANKED, Set.of(), 5), 1e-9);
    assertEquals(0.0, RankingMetrics.ndcgAt(RANKED, Set.of("z"), 5), 1e-9);
  }

  @Test
  void recallIsTheShareOfRelevantDocumentsInsideK() {
    assertEquals(1.0, RankingMetrics.recallAt(RANKED, Set.of("a"), 5), 1e-9);
    assertEquals(0.5, RankingMetrics.recallAt(RANKED, Set.of("a", "z"), 5), 1e-9);
    assertEquals(2.0 / 3, RankingMetrics.recallAt(RANKED, Set.of("a", "b", "z"), 5), 1e-9);
    // "c" is at rank 3, outside k=2.
    assertEquals(0.5, RankingMetrics.recallAt(RANKED, Set.of("a", "c"), 2), 1e-9);
    assertEquals(0.0, RankingMetrics.recallAt(RANKED, Set.of("a"), 0), 1e-9);
  }

  @Test
  void metricsHandleAnEmptyResultList() {
    assertEquals(0.0, RankingMetrics.reciprocalRank(List.of(), Set.of("a")), 1e-9);
    assertEquals(0.0, RankingMetrics.ndcgAt(List.of(), Set.of("a"), 5), 1e-9);
    assertEquals(0.0, RankingMetrics.recallAt(List.of(), Set.of("a"), 5), 1e-9);
  }
}
