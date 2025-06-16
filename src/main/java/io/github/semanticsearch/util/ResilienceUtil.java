package io.github.semanticsearch.util;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for resilience patterns. Provides methods for retry and circuit breaking without
 * external dependencies.
 */
@Component
@Slf4j
public class ResilienceUtil {

  /**
   * Execute with retry logic.
   *
   * @param callable Operation to execute
   * @param maxRetries Maximum number of retries
   * @param retryDelayMs Delay between retries in milliseconds
   * @param <T> Return type
   * @return Result of the operation
   * @throws Exception if all retries fail
   */
  public <T> T executeWithRetry(Callable<T> callable, int maxRetries, long retryDelayMs)
      throws Exception {
    int attempts = 0;
    Exception lastException = null;

    while (attempts <= maxRetries) {
      try {
        return callable.call();
      } catch (Exception e) {
        lastException = e;
        attempts++;

        if (attempts <= maxRetries) {
          log.warn("Retry attempt {} of {} failed: {}", attempts, maxRetries, e.getMessage());
          try {
            Thread.sleep(retryDelayMs * attempts); // Exponential backoff
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw e;
          }
        }
      }
    }

    throw lastException;
  }

  /**
   * Execute with fallback.
   *
   * @param supplier Primary operation to execute
   * @param fallback Fallback operation if primary fails
   * @param <T> Return type
   * @return Result of primary or fallback operation
   */
  public <T> T executeWithFallback(Supplier<T> supplier, Supplier<T> fallback) {
    try {
      return supplier.get();
    } catch (Exception e) {
      log.warn("Operation failed, using fallback: {}", e.getMessage());
      return fallback.get();
    }
  }

  /**
   * Execute with timeout.
   *
   * @param callable Operation to execute
   * @param timeoutMs Timeout in milliseconds
   * @param <T> Return type
   * @return Result of the operation
   * @throws Exception if operation times out or fails
   */
  public <T> T executeWithTimeout(Callable<T> callable, long timeoutMs) throws Exception {
    final Thread[] threadHolder = new Thread[1];
    final T[] resultHolder = (T[]) new Object[1];
    final Exception[] exceptionHolder = new Exception[1];

    Thread executionThread =
        new Thread(
            () -> {
              try {
                resultHolder[0] = callable.call();
              } catch (Exception e) {
                exceptionHolder[0] = e;
              }
            });

    threadHolder[0] = executionThread;
    executionThread.start();
    executionThread.join(timeoutMs);

    if (executionThread.isAlive()) {
      executionThread.interrupt();
      throw new RuntimeException("Operation timed out after " + timeoutMs + "ms");
    }

    if (exceptionHolder[0] != null) {
      throw exceptionHolder[0];
    }

    return resultHolder[0];
  }
}
