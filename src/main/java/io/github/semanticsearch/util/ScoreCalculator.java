package io.github.semanticsearch.util;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import io.github.semanticsearch.config.SearchProperties;
import io.github.semanticsearch.model.Document;

public final class ScoreCalculator {

  private ScoreCalculator() {}

  public static double blendScores(
      double vectorScore, double lexicalScore, SearchProperties properties) {
    if (!properties.isHybridEnabled()) {
      return clamp(vectorScore);
    }
    double weight =
        "B".equalsIgnoreCase(properties.getScoringProfile())
            ? properties.getHybridVectorWeightProfileB()
            : properties.getHybridVectorWeight();
    double w = Math.min(1.0, Math.max(0.0, weight));
    return clamp(w * vectorScore + (1 - w) * lexicalScore);
  }

  public static double applyMetadataBoosts(
      Document document, double score, Map<String, Double> metadataBoosts) {
    if (metadataBoosts == null || metadataBoosts.isEmpty()) {
      return clamp(score);
    }
    Map<String, String> metadata =
        document.getMetadata() == null ? Map.of() : document.getMetadata();
    double boosted = score;
    for (Map.Entry<String, Double> boost : metadataBoosts.entrySet()) {
      if (metadata.containsKey(boost.getKey())) {
        boosted += boost.getValue();
      }
    }
    return clamp(boosted);
  }

  /**
   * Scale a score by document age, bounded so that freshness breaks ties between comparable
   * documents without deciding on its own whether a document is returned at all.
   *
   * <p>The multiplier runs from 1.0 at age zero down to {@link SearchProperties#getRecencyFloor()}
   * as age grows, halving the distance between the two every half-life. An unbounded exponential
   * would fall below any {@code minScore} within a few half-lives, so a corpus older than that
   * answers every query with nothing.
   */
  public static double applyRecency(Document document, double score, SearchProperties properties) {
    long halfLifeSeconds = properties.getRecencyHalfLifeSeconds();
    if (!properties.isRecencyEnabled() || halfLifeSeconds <= 0) {
      return clamp(score);
    }
    Instant base =
        document.getUpdatedAt() != null
            ? document.getUpdatedAt()
            : (document.getCreatedAt() != null ? document.getCreatedAt() : Instant.now());
    double ageSeconds = Duration.between(base, Instant.now()).abs().toSeconds();
    double decay = Math.pow(0.5, ageSeconds / (double) halfLifeSeconds);
    double floor = Math.min(1.0, Math.max(0.0, properties.getRecencyFloor()));
    return clamp(score * (floor + (1.0 - floor) * decay));
  }

  public static double clamp(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return 0.0;
    }
    return Math.max(0.0, Math.min(1.0, value));
  }
}
