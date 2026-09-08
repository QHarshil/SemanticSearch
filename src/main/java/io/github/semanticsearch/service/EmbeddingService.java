package io.github.semanticsearch.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.semanticsearch.config.EmbeddingProvider;

/**
 * Generates text embeddings, either in-process or through a hosted provider.
 *
 * <p>{@code embedding.provider} chooses between them. {@link HashingEmbedder} is the default
 * because it needs no model file and no API key; it is lexical, so it matches shared words and not
 * shared meaning. {@link OnnxEmbedder} and the hosted provider are both semantic.
 *
 * <p>All vectors this service returns have {@link #dimensions()} elements, whichever provider
 * produced them, so the search index mapping only has to agree with one number.
 */
@Service
public class EmbeddingService {

  private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

  private final TextEmbedder localEmbedder;
  private final OpenAiEmbeddingClient openAiClient;
  private final boolean local;
  private final int dimensions;
  private final String cacheNamespace;

  public EmbeddingService(
      TextEmbedder localEmbedder,
      @Nullable OpenAiEmbeddingClient openAiClient,
      @Value("${embedding.provider:hashing}") String provider,
      @Value("${embedding.dimensions:256}") int dimensions,
      @Value("${embedding.model:text-embedding-3-small}") String model) {
    this.localEmbedder = localEmbedder;
    this.openAiClient = openAiClient;
    this.local =
        EmbeddingProvider.from(provider) != EmbeddingProvider.OPENAI || openAiClient == null;
    // An in-process model fixes its own width, so it is the authority rather than
    // the property: asking a 384-dimension transformer for 256 numbers is not a
    // thing it can do, and a mismatch here reaches the index as a mapping the
    // vectors cannot satisfy.
    this.dimensions = this.local ? localEmbedder.dimensions() : dimensions;
    this.cacheNamespace =
        (this.local ? localEmbedder.modelId() : "openai/" + model) + "/" + this.dimensions;
  }

  /** Width of every vector this service returns. */
  public int dimensions() {
    return dimensions;
  }

  /** True when embeddings are produced in this process instead of by a hosted provider. */
  public boolean isLocal() {
    return local;
  }

  /**
   * Prefix on every embedding cache key, identifying the provider, model and vector width that
   * produced the value.
   *
   * <p>Text alone does not identify an embedding. Vectors from different models occupy unrelated
   * coordinate spaces, and a vector of the wrong width cannot be scored against the index at all.
   * Because a cache hit returns before the method body runs, a key without these components would
   * serve vectors built under a previous configuration, and skip the width check below, until every
   * entry aged out.
   */
  public String cacheNamespace() {
    return cacheNamespace;
  }

  /**
   * Embed text into a vector of {@link #dimensions()} elements.
   *
   * <p>Keyed on the text itself rather than {@code text.hashCode()}: a 32-bit non-cryptographic
   * hash can collide, and a collision here would silently hand one document another document's
   * embedding.
   *
   * <p>Empty results are not cached. They mean the provider failed or returned an unusable vector,
   * which is a transient condition; caching it would pin "this text has no embedding" for the full
   * cache TTL and keep the document unsearchable long after the provider recovered.
   *
   * @return the embedding, or an empty list if the hosted provider could not be reached
   */
  @Cacheable(
      value = "embeddings",
      key = "#root.target.cacheNamespace() + '|' + #text",
      unless = "#result.isEmpty()")
  @Retry(name = "embedding")
  @CircuitBreaker(name = "embedding", fallbackMethod = "fallbackEmbed")
  public List<Double> embed(String text) {
    if (isLocal()) {
      return toList(localEmbedder.embed(text));
    }

    List<Double> embedding = openAiClient.embed(text);
    if (embedding.isEmpty()) {
      log.warn("Embedding provider returned no data");
      return Collections.emptyList();
    }
    if (embedding.size() != dimensions) {
      // Indexing a differently sized vector would be rejected by Elasticsearch,
      // or silently score as zero in the in-memory index. Fail loudly instead.
      log.error(
          "Embedding provider returned {} dimensions but the index expects {}. "
              + "Align embedding.dimensions with the configured model.",
          embedding.size(),
          dimensions);
      return Collections.emptyList();
    }
    return embedding;
  }

  /**
   * Returns no embedding when the provider is unavailable.
   *
   * <p>Substituting a local vector here would be worse than failing: the local and hosted models
   * occupy unrelated vector spaces, so the query vector would be compared against documents indexed
   * by the other model. That returns confident-looking scores computed from noise. Callers already
   * treat an empty vector as "no results".
   */
  private List<Double> fallbackEmbed(String text, Exception e) {
    log.warn("Embedding provider unavailable; returning no embedding", e);
    return Collections.emptyList();
  }

  private static List<Double> toList(double[] vector) {
    return Arrays.stream(vector).boxed().toList();
  }
}
