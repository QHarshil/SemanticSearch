package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Covers the wiring EmbeddingService owns: provider selection, the declared vector width, the cache
 * namespace and the behaviour when a hosted provider fails.
 *
 * <p>Constructed directly rather than through {@code @SpringBootTest} so that {@code embed()} runs
 * its own body on every call. Through the container it is {@code @Cacheable}, and a second call
 * with the same text would be answered by the cache without reaching the code under test.
 * Determinism of the vectors themselves is covered in {@link HashingEmbedderTest}.
 */
class EmbeddingServiceTest {

  private static final int DIMENSIONS = 128;
  private static final String MODEL = "text-embedding-3-small";

  private final HashingEmbedder embedder = new HashingEmbedder(DIMENSIONS);

  @Test
  void usesTheLocalEmbedderWhenLocalIsEnabled() {
    EmbeddingService service = localService(DIMENSIONS, failingClient());

    List<Double> embedding = service.embed("semantic search works");

    assertTrue(service.isLocal());
    assertEquals(DIMENSIONS, embedding.size());
    assertFalse(embedding.isEmpty());
  }

  @Test
  void fallsBackToTheLocalEmbedderWhenNoProviderIsConfigured() {
    // A null client is the "no API key supplied" case; the service must still work.
    EmbeddingService service = new EmbeddingService(embedder, null, false, DIMENSIONS, MODEL);

    assertTrue(service.isLocal());
    assertEquals(DIMENSIONS, service.embed("anything").size());
  }

  @Test
  void reportsTheDimensionTheIndexMappingIsBuiltFrom() {
    EmbeddingService service = localService(DIMENSIONS, null);

    assertEquals(DIMENSIONS, service.dimensions());
    assertEquals(
        service.dimensions(),
        service.embed("any text at all").size(),
        "the advertised dimension must match the vectors actually produced, "
            + "since IndexService builds the dense_vector mapping from it");
  }

  @Test
  void returnsNothingWhenTheProviderRepliesWithAMismatchedWidth() {
    // Indexing a wrong-width vector is rejected by Elasticsearch and scores as
    // zero in the in-memory index, so an empty result is the honest outcome.
    OpenAiEmbeddingClient wrongWidth = clientReturning(List.of(0.1, 0.2, 0.3));
    EmbeddingService service = new EmbeddingService(embedder, wrongWidth, false, DIMENSIONS, MODEL);

    assertTrue(service.embed("text").isEmpty());
  }

  @Test
  void usesTheProviderResponseWhenTheWidthMatches() {
    List<Double> provided = vectorOfWidth();
    EmbeddingService service =
        new EmbeddingService(embedder, clientReturning(provided), false, DIMENSIONS, MODEL);

    assertEquals(provided, service.embed("text"));
    assertFalse(service.isLocal());
  }

  /**
   * The cache is keyed on namespace plus text, so any configuration change that alters the vector
   * for a given text has to change the namespace. Were these equal, switching configuration would
   * keep serving vectors the new setup cannot use.
   */
  @Test
  void theCacheNamespaceSeparatesProviderModelAndWidth() {
    String local = localService(DIMENSIONS, null).cacheNamespace();
    String narrower =
        new EmbeddingService(new HashingEmbedder(64), null, true, 64, MODEL).cacheNamespace();
    String hosted =
        new EmbeddingService(embedder, clientReturning(vectorOfWidth()), false, DIMENSIONS, MODEL)
            .cacheNamespace();
    String otherModel =
        new EmbeddingService(
                embedder,
                clientReturning(vectorOfWidth()),
                false,
                DIMENSIONS,
                "text-embedding-3-large")
            .cacheNamespace();

    assertNotEquals(local, narrower, "a different vector width must not share cache entries");
    assertNotEquals(local, hosted, "local and hosted vectors must not share cache entries");
    assertNotEquals(hosted, otherModel, "two models must not share cache entries");
  }

  private EmbeddingService localService(int dimensions, OpenAiEmbeddingClient client) {
    return new EmbeddingService(new HashingEmbedder(dimensions), client, true, dimensions, MODEL);
  }

  private static List<Double> vectorOfWidth() {
    Double[] values = new Double[DIMENSIONS];
    for (int i = 0; i < DIMENSIONS; i++) {
      values[i] = i / (double) DIMENSIONS;
    }
    return List.of(values);
  }

  private static OpenAiEmbeddingClient clientReturning(List<Double> embedding) {
    return new OpenAiEmbeddingClient(
        "http://localhost", "key", "model", DIMENSIONS, java.time.Duration.ofSeconds(1)) {
      @Override
      public List<Double> embed(String text) {
        return embedding;
      }
    };
  }

  private static OpenAiEmbeddingClient failingClient() {
    return new OpenAiEmbeddingClient(
        "http://localhost", "key", "model", DIMENSIONS, java.time.Duration.ofSeconds(1)) {
      @Override
      public List<Double> embed(String text) {
        throw new AssertionError("the hosted provider must not be called when local is enabled");
      }
    };
  }
}
