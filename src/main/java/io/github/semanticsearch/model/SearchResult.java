package io.github.semanticsearch.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Data transfer object for search results. Contains document information and relevance score. */
public class SearchResult {

  private UUID id;
  private String title;
  private String content;
  private Map<String, String> metadata;
  private double score;
  private List<String> highlights;

  public SearchResult() {}

  public SearchResult(
      UUID id,
      String title,
      String content,
      Map<String, String> metadata,
      double score,
      List<String> highlights) {
    this.id = id;
    this.title = title;
    this.content = content;
    this.metadata = metadata;
    this.score = score;
    this.highlights = highlights;
  }

  public static Builder builder() {
    return new Builder();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public Map<String, String> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, String> metadata) {
    this.metadata = metadata;
  }

  public double getScore() {
    return score;
  }

  public void setScore(double score) {
    this.score = score;
  }

  public List<String> getHighlights() {
    return highlights;
  }

  public void setHighlights(List<String> highlights) {
    this.highlights = highlights;
  }

  public static final class Builder {
    private UUID id;
    private String title;
    private String content;
    private Map<String, String> metadata;
    private double score;
    private List<String> highlights;

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public Builder title(String title) {
      this.title = title;
      return this;
    }

    public Builder content(String content) {
      this.content = content;
      return this;
    }

    public Builder metadata(Map<String, String> metadata) {
      this.metadata = metadata;
      return this;
    }

    public Builder score(double score) {
      this.score = score;
      return this;
    }

    public Builder highlights(List<String> highlights) {
      this.highlights = highlights;
      return this;
    }

    public SearchResult build() {
      return new SearchResult(id, title, content, metadata, score, highlights);
    }
  }
}
