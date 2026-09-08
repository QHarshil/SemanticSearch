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

  /**
   * Reciprocal rank fusion of a document's positions in the two candidate lists.
   *
   * <p>Each list a document appears in contributes {@code 1 / (k + rank)}; being absent from one
   * contributes nothing. Only positions are read, so the two lists need not agree on what a score
   * means, which is the point of the method.
   *
   * <p>Divided by the best score any document could reach, so the result stays in {@code [0,1]}
   * alongside the blended score and {@code minScore} keeps a single meaning. The divisor counts the
   * lists the configuration runs, not the lists that happened to return something, or a query no
   * lexical term matched would score its vector hits as though they had swept both lists.
   *
   * @param vectorRank 1-based position in the vector ranking, or null if absent from it
   * @param lexicalRank 1-based position in the lexical ranking, or null if absent from it
   */
  public static double reciprocalRankFusion(
      Integer vectorRank, Integer lexicalRank, SearchProperties properties) {
    int k = Math.max(1, properties.getRrfK());
    double score = 0.0;
    if (vectorRank != null) {
      score += 1.0 / (k + vectorRank);
    }
    if (lexicalRank != null) {
      score += 1.0 / (k + lexicalRank);
    }
    int lists = properties.isHybridEnabled() ? 2 : 1;
    double best = lists / (double) (k + 1);
    return clamp(score / best);
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
