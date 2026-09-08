package io.github.semanticsearch.util;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.semanticsearch.config.SearchProperties;
import io.github.semanticsearch.model.Document;

class ScoreCalculatorTest {

  @Test
  void blendsTheTwoScoresByTheConfiguredWeight() {
    SearchProperties props = new SearchProperties();

    props.setHybridVectorWeight(0.8);
    assertEquals(0.8, ScoreCalculator.blendScores(1.0, 0.0, props), 1e-6);
    assertEquals(0.2, ScoreCalculator.blendScores(0.0, 1.0, props), 1e-6);
    assertEquals(0.44, ScoreCalculator.blendScores(0.5, 0.2, props), 1e-6);

    props.setHybridVectorWeight(0.5);
    assertEquals(0.5, ScoreCalculator.blendScores(1.0, 0.0, props), 1e-6);
  }

  @Test
  void aWeightOutsideZeroToOneIsClampedRatherThanScalingTheScore() {
    SearchProperties props = new SearchProperties();

    props.setHybridVectorWeight(2.0);
    assertEquals(1.0, ScoreCalculator.blendScores(1.0, 0.0, props), 1e-6);

    props.setHybridVectorWeight(-1.0);
    assertEquals(1.0, ScoreCalculator.blendScores(0.0, 1.0, props), 1e-6);
  }

  @Test
  void withHybridOffTheVectorScoreIsTheScore() {
    SearchProperties props = new SearchProperties();
    props.setHybridEnabled(false);
    props.setHybridVectorWeight(0.1);

    assertEquals(0.6, ScoreCalculator.blendScores(0.6, 0.9, props), 1e-6);
  }

  @Test
  void reciprocalRankFusionScoresOneOnlyWhenBothListsRankItFirst() {
    SearchProperties props = new SearchProperties();
    props.setHybridEnabled(true);
    props.setRrfK(60);

    assertEquals(1.0, ScoreCalculator.reciprocalRankFusion(1, 1, props), 1e-9);
    // Present in one list only: half of the best, since the divisor counts both
    // configured lists.
    assertEquals(0.5, ScoreCalculator.reciprocalRankFusion(1, null, props), 1e-9);
    assertEquals(0.5, ScoreCalculator.reciprocalRankFusion(null, 1, props), 1e-9);
    assertEquals(0.0, ScoreCalculator.reciprocalRankFusion(null, null, props), 1e-9);
  }

  @Test
  void reciprocalRankFusionFallsOffWithRank() {
    SearchProperties props = new SearchProperties();
    props.setHybridEnabled(true);
    props.setRrfK(60);

    // Rank 2 in both lists: 2 * (1/62), against a best of 2 * (1/61).
    assertEquals(61.0 / 62.0, ScoreCalculator.reciprocalRankFusion(2, 2, props), 1e-9);
    assertTrue(
        ScoreCalculator.reciprocalRankFusion(1, 1, props)
            > ScoreCalculator.reciprocalRankFusion(1, 2, props));
    assertTrue(
        ScoreCalculator.reciprocalRankFusion(1, 2, props)
            > ScoreCalculator.reciprocalRankFusion(2, 2, props));
  }

  @Test
  void aSmallerKSharpensTheGapBetweenRanks() {
    SearchProperties props = new SearchProperties();
    props.setHybridEnabled(true);

    props.setRrfK(60);
    double flat = ScoreCalculator.reciprocalRankFusion(10, 10, props);
    props.setRrfK(5);
    double sharp = ScoreCalculator.reciprocalRankFusion(10, 10, props);

    assertTrue(sharp < flat, "k=5 gave " + sharp + ", k=60 gave " + flat);
  }

  @Test
  void withHybridOffRankFusionNormalisesAgainstTheOneListItRuns() {
    SearchProperties props = new SearchProperties();
    props.setHybridEnabled(false);
    props.setRrfK(60);

    // Dividing by two lists here would halve every score on a configuration that
    // only ever has one, and minScore would mean something different.
    assertEquals(1.0, ScoreCalculator.reciprocalRankFusion(1, null, props), 1e-9);
  }

  @Test
  void appliesMetadataBoosts() {
    Document doc = new Document();
    doc.setMetadata(Map.of("topic", "search"));
    double boosted =
        ScoreCalculator.applyMetadataBoosts(doc, 0.5, Map.of("topic", 0.2, "other", 0.1));
    assertEquals(0.7, boosted, 1e-6);
  }

  @Test
  void oneHalfLifeSpendsHalfTheAvailableDecay() {
    SearchProperties props = recencyProps(3600, 0.7);
    // At one half-life the multiplier sits halfway between the floor and 1.0:
    // 0.7 + 0.3 * 0.5. The tolerance covers a second of wall clock between
    // building the document and reading the age, which moves the result by 3e-5.
    assertEquals(0.85, ScoreCalculator.applyRecency(aged(3600), 1.0, props), 1e-3);
  }

  @Test
  void freshDocumentKeepsItsWholeScore() {
    SearchProperties props = recencyProps(3600, 0.7);
    assertEquals(0.6, ScoreCalculator.applyRecency(aged(0), 0.6, props), 1e-3);
  }

  @Test
  void ageCannotPushAScoreBelowTheFloor() {
    SearchProperties props = recencyProps(604800, 0.7);
    // Ten years is over five hundred half-lives, so the exponential term is zero
    // to within double precision and only the floor is left.
    double decayed = ScoreCalculator.applyRecency(aged(3650L * 86400), 0.53, props);

    assertEquals(0.53 * 0.7, decayed, 1e-6);
    // 0.2 is the default minScore. An unbounded exponential leaves this document
    // scoring 1e-160, so search drops it however well it matches the query.
    assertTrue(decayed > 0.2, "aged document scored " + decayed + ", below the default minScore");
  }

  @Test
  void olderDocumentsScoreBelowNewerOnesWithTheSameRelevance() {
    SearchProperties props = recencyProps(604800, 0.7);
    double fresh = ScoreCalculator.applyRecency(aged(0), 0.5, props);
    double week = ScoreCalculator.applyRecency(aged(604800), 0.5, props);
    double year = ScoreCalculator.applyRecency(aged(365L * 86400), 0.5, props);

    assertTrue(fresh > week, "fresh " + fresh + " should outrank one week old " + week);
    assertTrue(week > year, "one week old " + week + " should outrank one year old " + year);
  }

  @Test
  void aZeroFloorGivesTheRawExponential() {
    SearchProperties props = recencyProps(3600, 0.0);
    assertEquals(0.25, ScoreCalculator.applyRecency(aged(7200), 1.0, props), 1e-3);
  }

  @Test
  void aDocumentWithNoUpdateTimestampAgesFromItsCreationTime() {
    SearchProperties props = recencyProps(3600, 0.7);
    Document created = new Document();
    created.setCreatedAt(Instant.now().minusSeconds(3600));

    assertEquals(0.85, ScoreCalculator.applyRecency(created, 1.0, props), 1e-3);
  }

  @Test
  void aDocumentWithNoTimestampsAtAllCountsAsNew() {
    SearchProperties props = recencyProps(3600, 0.7);

    assertEquals(1.0, ScoreCalculator.applyRecency(new Document(), 1.0, props), 1e-3);
  }

  @Test
  void clampTurnsAnUnusableNumberIntoZeroRatherThanPropagatingIt() {
    // A NaN score sorts unpredictably and serialises as null in JSON, so it would
    // reach clients as a result with no score at all.
    assertEquals(0.0, ScoreCalculator.clamp(Double.NaN), 1e-9);
    assertEquals(0.0, ScoreCalculator.clamp(Double.POSITIVE_INFINITY), 1e-9);
    assertEquals(0.0, ScoreCalculator.clamp(Double.NEGATIVE_INFINITY), 1e-9);
    assertEquals(1.0, ScoreCalculator.clamp(1.5), 1e-9);
    assertEquals(0.0, ScoreCalculator.clamp(-0.5), 1e-9);
  }

  @Test
  void anUnknownFusionMethodIsRejectedWhereItBinds() {
    SearchProperties props = new SearchProperties();

    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> props.setFusion("rff"));

    assertTrue(thrown.getMessage().contains("blend, rrf"), thrown.getMessage());
    assertEquals("blend", props.getFusion(), "the rejected value must not be stored");
  }

  @Test
  void decayIsSkippedWhenDisabledOrTheHalfLifeIsUnset() {
    Document old = aged(365L * 86400);

    SearchProperties disabled = recencyProps(604800, 0.7);
    disabled.setRecencyEnabled(false);
    assertEquals(0.5, ScoreCalculator.applyRecency(old, 0.5, disabled), 1e-9);

    assertEquals(0.5, ScoreCalculator.applyRecency(old, 0.5, recencyProps(0, 0.7)), 1e-9);
  }

  private static SearchProperties recencyProps(long halfLifeSeconds, double floor) {
    SearchProperties props = new SearchProperties();
    props.setRecencyEnabled(true);
    props.setRecencyHalfLifeSeconds(halfLifeSeconds);
    props.setRecencyFloor(floor);
    return props;
  }

  private static Document aged(long ageSeconds) {
    Document doc = new Document();
    doc.setUpdatedAt(Instant.now().minusSeconds(ageSeconds));
    return doc;
  }
}
