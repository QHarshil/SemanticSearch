package io.github.semanticsearch.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The three ways this service can turn text into a vector. Selected by {@code embedding.provider}.
 */
public enum EmbeddingProvider {

  /** Feature hashing over words and character n-grams. Lexical, in-process, needs nothing. */
  HASHING,

  /** A sentence-transformer running locally through ONNX Runtime. Semantic, needs a model file. */
  ONNX,

  /** A hosted embedding API. Semantic, needs a key and a network round trip per call. */
  OPENAI;

  /**
   * @throws IllegalArgumentException naming the accepted values, since a typo here otherwise
   *     silently selects a fallback and every vector in the index is built by the wrong model
   */
  public static EmbeddingProvider from(String value) {
    String name = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    return Arrays.stream(values())
        .filter(provider -> provider.name().equals(name))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unknown embedding.provider '"
                        + value
                        + "'. Expected one of "
                        + Arrays.stream(values())
                            .map(p -> p.name().toLowerCase(Locale.ROOT))
                            .collect(Collectors.joining(", "))
                        + "."));
  }

  /** The value as it is written in configuration. */
  public String configValue() {
    return name().toLowerCase(Locale.ROOT);
  }
}
