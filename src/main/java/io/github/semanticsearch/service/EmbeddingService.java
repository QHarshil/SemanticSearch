package io.github.semanticsearch.service;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.service.OpenAiService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for generating text embeddings using OpenAI API. Includes caching, retry, and circuit
 * breaker patterns.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

  private final OpenAiService openAiService;

  @Value("${openai.embedding.model:text-embedding-ada-002}")
  private String embeddingModel;

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
      log.warn("No embeddings returned from OpenAI API");
      return Collections.emptyList();
    }

    log.debug(
        "Successfully generated embedding with {} dimensions",
        embeddings.get(0).getEmbedding().size());
    return embeddings.get(0).getEmbedding();
  }

  /**
   * Fallback method for embedding generation when OpenAI API fails. Returns an empty list as
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
