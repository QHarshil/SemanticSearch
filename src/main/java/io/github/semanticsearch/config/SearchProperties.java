package io.github.semanticsearch.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search")
public class SearchProperties {
  /** When true, blend lexical and vector signals. */
  private boolean hybridEnabled = true;

  /** Weight for vector similarity in hybrid scoring (0-1). */
  private double hybridVectorWeight = 0.7;
  /** Alternate weight for profile B (A/B testing). */
  private double hybridVectorWeightProfileB = 0.5;

  /** Scoring profile label to allow A/B comparisons (e.g., A or B). */
  private String scoringProfile = "A";

  /** Recency half-life in seconds for exponential decay; set 0 or negative to disable. */
  private long recencyHalfLifeSeconds = 604800; // 7 days default

  /** Enable recency decay. */
  private boolean recencyEnabled = true;

  /** BM25 parameters for the lexical component. */
  private double bm25K1 = 1.2;
  private double bm25B = 0.75;

  /** Optional metadata boosts. Key = metadata key, value = additive boost. */
  private Map<String, Double> metadataBoosts = new HashMap<>();

  public boolean isHybridEnabled() {
    return hybridEnabled;
  }

  public void setHybridEnabled(boolean hybridEnabled) {
    this.hybridEnabled = hybridEnabled;
  }

  public double getHybridVectorWeight() {
    return hybridVectorWeight;
  }

  public void setHybridVectorWeight(double hybridVectorWeight) {
    this.hybridVectorWeight = hybridVectorWeight;
  }

  public double getHybridVectorWeightProfileB() {
    return hybridVectorWeightProfileB;
  }

  public void setHybridVectorWeightProfileB(double hybridVectorWeightProfileB) {
    this.hybridVectorWeightProfileB = hybridVectorWeightProfileB;
  }

  public String getScoringProfile() {
    return scoringProfile;
  }

  public void setScoringProfile(String scoringProfile) {
    this.scoringProfile = scoringProfile;
  }

  public long getRecencyHalfLifeSeconds() {
    return recencyHalfLifeSeconds;
  }

  public void setRecencyHalfLifeSeconds(long recencyHalfLifeSeconds) {
    this.recencyHalfLifeSeconds = recencyHalfLifeSeconds;
  }

  public boolean isRecencyEnabled() {
    return recencyEnabled;
  }

  public void setRecencyEnabled(boolean recencyEnabled) {
    this.recencyEnabled = recencyEnabled;
  }

  public double getBm25K1() {
    return bm25K1;
  }

  public void setBm25K1(double bm25K1) {
    this.bm25K1 = bm25K1;
  }

  public double getBm25B() {
    return bm25B;
  }

  public void setBm25B(double bm25B) {
    this.bm25B = bm25B;
  }

  public Map<String, Double> getMetadataBoosts() {
    return metadataBoosts;
  }

  public void setMetadataBoosts(Map<String, Double> metadataBoosts) {
    this.metadataBoosts = metadataBoosts != null ? metadataBoosts : new HashMap<>();
  }
}
