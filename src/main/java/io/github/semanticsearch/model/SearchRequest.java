package io.github.semanticsearch.model;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
/** Data transfer object for search requests. Contains query text and optional search parameters. */
public class SearchRequest {

  @NotBlank(message = "Query text is required")
  private String query;

  @Positive(message = "Limit must be positive")
  private int limit = 10;

  @DecimalMin(value = "0.0", inclusive = true, message = "Min score must be >= 0")
  @DecimalMax(value = "1.0", inclusive = true, message = "Min score must be <= 1")
  private double minScore = 0.7;

  private Map<String, String> filters = Map.of();
  private List<String> fields = List.of();

  private boolean includeContent = true;

  private boolean includeHighlights = true;

  public SearchRequest() {}

  public SearchRequest(
      String query,
      int limit,
      double minScore,
      Map<String, String> filters,
      List<String> fields,
      boolean includeContent,
      boolean includeHighlights) {
    this.query = query;
    this.limit = limit;
    this.minScore = minScore;
    this.filters = filters != null ? filters : Map.of();
    this.fields = fields != null ? fields : List.of();
    this.includeContent = includeContent;
    this.includeHighlights = includeHighlights;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  public int getLimit() {
    return limit;
  }

  public void setLimit(int limit) {
    this.limit = limit;
  }

  public double getMinScore() {
    return minScore;
  }

  public void setMinScore(double minScore) {
    this.minScore = minScore;
  }

  public Map<String, String> getFilters() {
    return filters;
  }

  public void setFilters(Map<String, String> filters) {
    this.filters = filters != null ? filters : Map.of();
  }

  public List<String> getFields() {
    return fields;
  }

  public void setFields(List<String> fields) {
    this.fields = fields != null ? fields : List.of();
  }

  public boolean isIncludeContent() {
    return includeContent;
  }

  public void setIncludeContent(boolean includeContent) {
    this.includeContent = includeContent;
  }

  public boolean isIncludeHighlights() {
    return includeHighlights;
  }

  public void setIncludeHighlights(boolean includeHighlights) {
    this.includeHighlights = includeHighlights;
  }

  public static final class Builder {
    private String query;
    private int limit = 10;
    private double minScore = 0.7;
    private Map<String, String> filters = Map.of();
    private List<String> fields = List.of();
    private boolean includeContent = true;
    private boolean includeHighlights = true;

    public Builder query(String query) {
      this.query = query;
      return this;
    }

    public Builder limit(int limit) {
      this.limit = limit;
      return this;
    }

    public Builder minScore(double minScore) {
      this.minScore = minScore;
      return this;
    }

    public Builder filters(Map<String, String> filters) {
      this.filters = filters;
      return this;
    }

    public Builder fields(List<String> fields) {
      this.fields = fields;
      return this;
    }

    public Builder includeContent(boolean includeContent) {
      this.includeContent = includeContent;
      return this;
    }

    public Builder includeHighlights(boolean includeHighlights) {
      this.includeHighlights = includeHighlights;
      return this;
    }

    public SearchRequest build() {
      return new SearchRequest(
          query, limit, minScore, filters, fields, includeContent, includeHighlights);
    }
  }
}
