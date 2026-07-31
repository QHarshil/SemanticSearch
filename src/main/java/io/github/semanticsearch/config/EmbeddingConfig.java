package io.github.semanticsearch.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.semanticsearch.service.HashingEmbedder;
import io.github.semanticsearch.service.OpenAiEmbeddingClient;

/** Wires the embedding providers. See {@code embedding.*} in application.yml. */
@Configuration
public class EmbeddingConfig {

  @Value("${embedding.dimensions:256}")
  private int dimensions;

  /**
   * Always available, so the service can start and answer queries without an API key. Used as the
   * active provider unless {@code embedding.local-enabled} is false.
   */
  @Bean
  public HashingEmbedder hashingEmbedder() {
    return new HashingEmbedder(dimensions);
  }

  /**
   * Only created when local embeddings are switched off, so the missing-API-key failure happens at
   * startup rather than on the first search.
   */
  @Bean
  @ConditionalOnProperty(name = "embedding.local-enabled", havingValue = "false")
  public OpenAiEmbeddingClient openAiEmbeddingClient(
      @Value("${embedding.api.key:}") String apiKey,
      @Value("${embedding.api.base-url:https://api.openai.com}") String baseUrl,
      @Value("${embedding.model:text-embedding-3-small}") String model,
      @Value("${embedding.timeout:30}") int timeoutSeconds) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "embedding.local-enabled is false, so an API key is required. "
              + "Set EMBEDDING_API_KEY, or leave embedding.local-enabled at its default to use the "
              + "built-in local embedder.");
    }
    return new OpenAiEmbeddingClient(
        baseUrl, apiKey, model, dimensions, Duration.ofSeconds(timeoutSeconds));
  }
}
