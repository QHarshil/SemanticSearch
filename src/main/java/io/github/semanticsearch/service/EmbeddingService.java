package io.github.semanticsearch.service;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.service.OpenAiService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Service for generating text embeddings via the configured provider. Includes caching, retry, and
 * circuit breaker patterns.
 */
@Service
public class EmbeddingService {

  private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

  private final OpenAiService openAiService;

  @Value("${openai.embedding.model:text-embedding-ada-002}")
  private String embeddingModel;

  public EmbeddingService(OpenAiService openAiService) {
    this.openAiService = openAiService;
  }

  /**
   * Generate embedding vector for the given text. Uses caching to avoid redundant API calls for the
   * same text.
   *
   * @param text Text to generate embedding for
   * @return List of double values representing the embedding vector
   */
  @Cacheable(value = "embeddings", key = "#text.hashCode()")
  @Retry(name = "openai")
  @CircuitBreaker(name = "openai", fallbackMethod = "fallbackEmbed")
  public List<Double> embed(String text) {
    log.debug("Generating embedding for text: {}", text.substring(0, Math.min(50, text.length())));

    EmbeddingRequest request =
        EmbeddingRequest.builder()
            .model(embeddingModel)
            .input(Collections.singletonList(text))
            .build();

    List<Embedding> embeddings = openAiService.createEmbeddings(request).getData();

    if (embeddings.isEmpty()) {
    log.warn("No embeddings returned from embedding provider");
      return Collections.emptyList();
    }

    log.debug(
        "Successfully generated embedding with {} dimensions",
        embeddings.get(0).getEmbedding().size());
    return embeddings.get(0).getEmbedding();
  }

  /**
   * Fallback method for embedding generation when the provider fails. Returns an empty list as
   * fallback.
   *
   * @param text Text that was being embedded
   * @param e Exception that triggered the fallback
   * @return Empty list as fallback
   */
  private List<Double> fallbackEmbed(String text, Exception e) {
    log.error(
        "Failed to generate embedding for text: {}",
        text.substring(0, Math.min(50, text.length())),
        e);
    return Collections.emptyList();
  }
}
