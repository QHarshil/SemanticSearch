package io.github.semanticsearch.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.theokanning.openai.service.OpenAiService;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

/**
 * Configuration for OpenAI client. Sets up the OpenAI client with API key and resilience patterns.
 */
@Configuration
public class OpenAiConfig {

  @Value("${openai.api.key}")
  private String apiKey;

  @Value("${openai.api.prompt:false}")
  private boolean promptForApiKey;

  @Value("${openai.timeout:30}")
  private int timeout;

  /**
   * Creates and configures the OpenAI service.
   *
   * @return Configured OpenAiService
   */
  @Bean
  public OpenAiService openAiService() {
    String resolvedKey = resolveApiKey();
    return new OpenAiService(resolvedKey, Duration.ofSeconds(timeout));
  }

  /**
   * Creates retry configuration for OpenAI API calls.
   *
   * @return RetryRegistry with configured retry policies
   */
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

  /**
   * Creates circuit breaker configuration for OpenAI API calls.
   *
   * @return CircuitBreakerConfig
   */
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
    if (apiKey != null && !apiKey.isBlank()) {
      return apiKey;
    }
    if (promptForApiKey) {
      var console = System.console();
      if (console != null) {
        char[] entered = console.readPassword("Enter OpenAI API key: ");
        if (entered != null && entered.length > 0) {
          return new String(entered);
        }
      }
    }
    throw new IllegalStateException(
        "OpenAI API key is required. Set openai.api.key or enable openai.api.prompt=true for interactive entry.");
  }
}
