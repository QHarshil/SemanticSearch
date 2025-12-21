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
  void appliesRecencyDecay() {
    Document doc = new Document();
    doc.setCreatedAt(Instant.now().minusSeconds(3600)); // 1 hour old
    double decayed = ScoreCalculator.applyRecency(doc, 1.0, true, 3600); // half-life 1 hour
    assertTrue(decayed < 1.0 && decayed > 0.4);
  }
}
