package io.github.semanticsearch.service;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Times the stages of a search separately.
 *
 * <p>A single figure for the whole request says a query was slow and not which part of it was.
 * These four stages fail for unrelated reasons: embedding is model inference, vector retrieval is a
 * graph walk or a linear scan, lexical retrieval is a postings walk that grows with how common the
 * query's terms are, and hydration is a database round trip whose cost tracks the candidate count.
 *
 * <p>Published at {@code /actuator/prometheus} as {@code search_stage_seconds} tagged by stage, and
 * {@code search_results} for how many results a query returned.
 */
@Component
public class SearchMetrics {

  static final String STAGE_TIMER = "search.stage";
  static final String RESULTS_SUMMARY = "search.results";

  private final MeterRegistry registry;

  public SearchMetrics(MeterRegistry registry) {
    this.registry = registry;
    // Registered up front so a stage that has not run yet reads as zero rather
    // than as a missing series, which a dashboard cannot tell apart from a
    // scrape failure.
    for (Stage stage : Stage.values()) {
      timer(stage);
    }
    DistributionSummary.builder(RESULTS_SUMMARY)
        .description("Results returned per search")
        .register(registry);
  }

  /** The parts of a search worth timing apart from each other. */
  public enum Stage {
    EMBED("embed"),
    VECTOR_RETRIEVAL("vector_retrieval"),
    LEXICAL_RETRIEVAL("lexical_retrieval"),
    HYDRATE("hydrate");

    private final String tag;

    Stage(String tag) {
      this.tag = tag;
    }
  }

  public <T> T time(Stage stage, Supplier<T> work) {
    return timer(stage).record(work);
  }

  public void recordResults(int count) {
    registry.summary(RESULTS_SUMMARY).record(count);
  }

  private Timer timer(Stage stage) {
    return Timer.builder(STAGE_TIMER)
        .description("Time spent in one stage of a search")
        .tag("stage", stage.tag)
        .publishPercentileHistogram()
        .register(registry);
  }
}
