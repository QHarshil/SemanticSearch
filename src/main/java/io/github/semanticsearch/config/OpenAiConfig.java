package io.github.semanticsearch.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.theokanning.openai.service.OpenAiService;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

/**
 * Configuration for the embedding provider client. Sets up the client with API key and resilience
 * patterns.
 */
@Configuration
public class OpenAiConfig {

  @Value("${embedding.api.key:}")
  private String apiKey;

  @Value("${embedding.api.prompt:false}")
  private boolean promptForApiKey;

  @Value("${embedding.timeout:30}")
  private int timeout;

  @Value("${embedding.stub-enabled:false}")
  private boolean stubEnabled;

  /**
   * Creates and configures the embedding provider service. Only instantiated when stub mode is
   * disabled.
   */
  @Bean
  @ConditionalOnProperty(name = "embedding.stub-enabled", havingValue = "false", matchIfMissing = true)
  public OpenAiService openAiService() {
    String resolvedKey = resolveApiKey();
    return new OpenAiService(resolvedKey, Duration.ofSeconds(timeout));
  }

  /** Creates retry configuration for embedding API calls. */
  @Bean
  public RetryRegistry retryRegistry() {
    RetryConfig retryConfig =
        RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(1000))
            .retryExceptions(RuntimeException.class)
            .build();

    return RetryRegistry.of(retryConfig);
  }

  /** Creates circuit breaker configuration for embedding API calls. */
  @Bean
  public CircuitBreakerConfig circuitBreakerConfig() {
    return CircuitBreakerConfig.custom()
        .failureRateThreshold(50)
        .waitDurationInOpenState(Duration.ofMillis(10000))
        .permittedNumberOfCallsInHalfOpenState(5)
        .slidingWindowSize(10)
        .build();
  }

  private String resolveApiKey() {
    if (stubEnabled) {
      return "stub-key";
    }
    if (apiKey != null && !apiKey.isBlank()) {
      return apiKey;
    }
    if (promptForApiKey) {
      var console = System.console();
      if (console != null) {
        char[] entered = console.readPassword("Enter embedding API key: ");
        if (entered != null && entered.length > 0) {
          return new String(entered);
        }
      }
    }
    throw new IllegalStateException(
        "Embedding API key is required. Set embedding.api.key or enable embedding.api.prompt=true for interactive entry.");
  }
}
