package io.github.semanticsearch.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * A search request.
 *
 * <p>Value-based {@code equals} and {@code hashCode} over every field, because {@code
 * SearchService.search} is cached on this object and two requests that differ anywhere must not
 * share an answer.
 */
public class SearchRequest {

  /**
   * Default floor on the score a result must reach to be returned.
   *
   * <p>Calibrated against the lexical embedder over the demo corpus, where natural-language queries
   * score their best match between 0.32 and 0.53. Over the eight gold queries a floor of 0.3 still
   * answers all eight and 0.4 answers only three, so 0.2 keeps a margin below that edge while
   * dropping the weakly matching tail. It takes those queries from 64 results to 22.
   *
   * <p>Another embedding model spreads scores differently. Under the ONNX provider the same queries
   * top out between 0.37 and 0.64, and a floor set for one model is not a floor for another.
   */
  public static final double DEFAULT_MIN_SCORE = 0.2;

  @NotBlank(message = "Query text is required")
  private String query;

  @Positive(message = "Limit must be positive")
  private int limit = 10;

  @DecimalMin(value = "0.0", inclusive = true, message = "Min score must be >= 0")
  @DecimalMax(value = "1.0", inclusive = true, message = "Min score must be <= 1")
  private double minScore = DEFAULT_MIN_SCORE;

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

  /**
   * Value semantics over every field. Required because this type is used as a cache key for {@code
   * searchResults}; identity semantics there mean the cache can never hit.
   */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof SearchRequest that)) {
      return false;
    }
    return limit == that.limit
        && Double.compare(minScore, that.minScore) == 0
        && includeContent == that.includeContent
        && includeHighlights == that.includeHighlights
        && Objects.equals(query, that.query)
        && Objects.equals(filters, that.filters)
        && Objects.equals(fields, that.fields);
  }

  @Override
  public int hashCode() {
    return Objects.hash(query, limit, minScore, filters, fields, includeContent, includeHighlights);
  }

  @Override
  public String toString() {
    return "SearchRequest{query='"
        + query
        + "', limit="
        + limit
        + ", minScore="
        + minScore
        + ", filters="
        + filters
        + ", fields="
        + fields
        + ", includeContent="
        + includeContent
        + ", includeHighlights="
        + includeHighlights
        + '}';
  }

  public static final class Builder {
    private String query;
    private int limit = 10;
    private double minScore = DEFAULT_MIN_SCORE;
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
