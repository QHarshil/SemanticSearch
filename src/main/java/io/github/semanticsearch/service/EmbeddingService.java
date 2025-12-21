package io.github.semanticsearch.service;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
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

  @Value("${embedding.model:text-embedding-3-small}")
  private String embeddingModel;

  @Value("${embedding.stub-enabled:false}")
  private boolean stubEnabled;

  @Value("${embedding.stub-dimensions:64}")
  private int stubDimensions;

  public EmbeddingService(@Nullable OpenAiService openAiService) {
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
  @Retry(name = "embedding")
  @CircuitBreaker(name = "embedding", fallbackMethod = "fallbackEmbed")
  public List<Double> embed(String text) {
    if (stubEnabled || openAiService == null) {
      return generateStubVector(text);
    }

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
    log.warn(
        "Embedding provider unavailable; using deterministic stub vector for text: {}",
        text.substring(0, Math.min(50, text.length())),
        e);
    return generateStubVector(text);
  }

  private List<Double> generateStubVector(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      double[] vector = new double[Math.max(4, stubDimensions)];

      for (int i = 0; i < vector.length; i++) {
        int b = hash[i % hash.length] & 0xFF;
        vector[i] = (b / 255.0) * 2.0 - 1.0; // normalize to [-1,1]
      }

      double norm =
          Math.sqrt(
              java.util.Arrays.stream(vector).map(v -> v * v).sum());
      if (norm > 0) {
        for (int i = 0; i < vector.length; i++) {
          vector[i] = vector[i] / norm;
        }
      }
      return java.util.Arrays.stream(vector).boxed().collect(Collectors.toList());
    } catch (NoSuchAlgorithmException ex) {
      log.error("Failed to create stub embedding vector", ex);
      return Collections.emptyList();
    }
  }
}
