package io.github.semanticsearch.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

public class ResilienceUtilTest {

  private final ResilienceUtil resilienceUtil = new ResilienceUtil();

  @Test
  void testExecuteWithRetry_Success() throws Exception {
    // Arrange
    Callable<String> successfulOperation = () -> "success";

    // Act
    String result = resilienceUtil.executeWithRetry(successfulOperation, 3, 100);

    // Assert
    assertEquals("success", result);
  }

  @Test
  void testExecuteWithRetry_EventualSuccess() throws Exception {
    // Arrange
    AtomicInteger attempts = new AtomicInteger(0);
    Callable<String> eventuallySuccessfulOperation =
        () -> {
          if (attempts.incrementAndGet() < 3) {
            throw new RuntimeException("Temporary failure");
          }
          return "success after retries";
        };

    // Act
    String result = resilienceUtil.executeWithRetry(eventuallySuccessfulOperation, 3, 100);

    // Assert
    assertEquals("success after retries", result);
    assertEquals(3, attempts.get());
  }

  @Test
  void testExecuteWithRetry_AllAttemptsFail() {
    // Arrange
    Callable<String> failingOperation =
        () -> {
          throw new RuntimeException("Operation failed");
        };

    // Act & Assert
    Exception exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              resilienceUtil.executeWithRetry(failingOperation, 3, 100);
            });
    assertEquals("Operation failed", exception.getMessage());
  }

  @Test
  void testExecuteWithFallback_PrimarySucceeds() {
    // Arrange
    Supplier<String> primaryOperation = () -> "primary result";
    Supplier<String> fallbackOperation = () -> "fallback result";

    // Act
    String result = resilienceUtil.executeWithFallback(primaryOperation, fallbackOperation);

    // Assert
    assertEquals("primary result", result);
  }

  @Test
  void testExecuteWithFallback_PrimaryFails() {
    // Arrange
    Supplier<String> primaryOperation =
        () -> {
          throw new RuntimeException("Primary operation failed");
        };
    Supplier<String> fallbackOperation = () -> "fallback result";

    // Act
    String result = resilienceUtil.executeWithFallback(primaryOperation, fallbackOperation);

    // Assert
    assertEquals("fallback result", result);
  }

  @Test
  void testExecuteWithTimeout_CompletesInTime() throws Exception {
    // Arrange
    Callable<String> quickOperation =
        () -> {
          Thread.sleep(50);
          return "completed quickly";
        };

    // Act
    String result = resilienceUtil.executeWithTimeout(quickOperation, 1000);

    // Assert
    assertEquals("completed quickly", result);
  }

  @Test
  void testExecuteWithTimeout_Timeout() {
    // Arrange
    Callable<String> slowOperation =
        () -> {
          Thread.sleep(500);
          return "completed slowly";
        };

    // Act & Assert
    Exception exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              resilienceUtil.executeWithTimeout(slowOperation, 100);
            });
    assertTrue(exception.getMessage().contains("timed out"));
  }
}
