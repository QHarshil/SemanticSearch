package io.github.semanticsearch.util;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.semanticsearch.config.SearchProperties;
import io.github.semanticsearch.model.Document;

class ScoreCalculatorTest {

  @Test
  void blendsAccordingToProfile() {
    SearchProperties props = new SearchProperties();
    props.setHybridVectorWeight(0.8);
    double score = ScoreCalculator.blendScores(1.0, 0.0, props);
    assertEquals(0.8, score, 1e-6);

    props.setScoringProfile("B");
    props.setHybridVectorWeightProfileB(0.5);
    assertEquals(0.5, ScoreCalculator.blendScores(1.0, 0.0, props), 1e-6);
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
    // 0.7 + 0.3 * 0.5.
    assertEquals(0.85, ScoreCalculator.applyRecency(aged(3600), 1.0, props), 1e-6);
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
    assertEquals(0.25, ScoreCalculator.applyRecency(aged(7200), 1.0, props), 1e-6);
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
