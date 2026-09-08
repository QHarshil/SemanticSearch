package io.github.semanticsearch.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.semanticsearch.service.HashingEmbedder;
import io.github.semanticsearch.service.OnnxEmbedder;
import io.github.semanticsearch.service.OpenAiEmbeddingClient;
import io.github.semanticsearch.service.TextEmbedder;
import io.github.semanticsearch.service.VerifiedFileCache;

/** Wires the embedding providers. See {@code embedding.*} in application.yml. */
@Configuration
public class EmbeddingConfig {

  @Value("${embedding.dimensions:256}")
  private int dimensions;

  @Value("${embedding.provider:hashing}")
  private String provider;

  /**
   * The in-process embedder.
   *
   * <p>Also built when the provider is {@code openai}, because {@link
   * io.github.semanticsearch.service.EmbeddingService} falls back to a local model when no API key
   * was supplied, and a service that cannot embed anything is worse than one that embeds lexically.
   *
   * <p>The default destroy method is inferred, so the ONNX session is closed at shutdown and the
   * hashing embedder, which holds nothing, is left alone.
   */
  @Bean
  public TextEmbedder textEmbedder(OnnxProperties onnx) {
    return switch (EmbeddingProvider.from(provider)) {
      case ONNX -> onnxEmbedder(onnx);
      case HASHING, OPENAI -> new HashingEmbedder(dimensions);
    };
  }

  private static TextEmbedder onnxEmbedder(OnnxProperties onnx) {
    VerifiedFileCache cache =
        new VerifiedFileCache(
            modelDir(onnx), onnx.isAutoDownload(), "embedding.onnx.auto-download");
    Path model = cache.resolve("model.onnx", onnx.getModelUrl(), onnx.getModelSha256());
    Path tokenizer =
        cache.resolve("tokenizer.json", onnx.getTokenizerUrl(), onnx.getTokenizerSha256());
    return new OnnxEmbedder(onnx.getModelId(), model, tokenizer, onnx.getMaxSequenceLength());
  }

  /**
   * Defaults to a per-model directory under the user's cache, so switching models does not leave
   * one model's weights sitting where the next one expects to find its own.
   */
  private static Path modelDir(OnnxProperties onnx) {
    if (onnx.getModelDir() != null) {
      return onnx.getModelDir();
    }
    String modelId = onnx.getModelId();
    String name = modelId.substring(modelId.lastIndexOf('/') + 1);
    return Path.of(
        System.getProperty("user.home"), ".cache", "semantic-search-java", "models", name);
  }

  /**
   * Only created for the hosted provider, so the missing-API-key failure happens at startup instead
   * of on the first search.
   */
  @Bean
  @ConditionalOnProperty(name = "embedding.provider", havingValue = "openai")
  public OpenAiEmbeddingClient openAiEmbeddingClient(
      @Value("${embedding.api.key:}") String apiKey,
      @Value("${embedding.api.base-url:https://api.openai.com}") String baseUrl,
      @Value("${embedding.model:text-embedding-3-small}") String model,
      @Value("${embedding.timeout:30}") int timeoutSeconds) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "embedding.provider is openai, so an API key is required. Set EMBEDDING_API_KEY, or "
              + "choose embedding.provider=onnx for a local semantic model or hashing for the "
              + "lexical default.");
    }
    return new OpenAiEmbeddingClient(
        baseUrl, apiKey, model, dimensions, Duration.ofSeconds(timeoutSeconds));
  }
}
