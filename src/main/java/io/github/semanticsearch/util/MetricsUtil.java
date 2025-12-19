package io.github.semanticsearch.util;

import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Utility class for monitoring and metrics. Provides methods for measuring and logging performance
 * metrics.
 */
@Component
public class MetricsUtil {

  private static final Logger log = LoggerFactory.getLogger(MetricsUtil.class);

  /**
   * Measure execution time of a function.
   *
   * @param function Function to measure
   * @param operationName Name of the operation for logging
   * @param <T> Input type
   * @param <R> Return type
   * @return Function that measures execution time
   */
  public <T, R> Function<T, R> measureExecutionTime(Function<T, R> function, String operationName) {
    return input -> {
      long startTime = System.currentTimeMillis();
      R result = function.apply(input);
      long endTime = System.currentTimeMillis();

      log.info("{} completed in {} ms", operationName, (endTime - startTime));
      return result;
    };
  }
}
