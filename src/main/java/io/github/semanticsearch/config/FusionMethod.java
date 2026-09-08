package io.github.semanticsearch.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** How the vector and lexical rankings are combined. Selected by {@code search.fusion}. */
public enum FusionMethod {

  /**
   * A weighted sum of the two scores. Keeps the magnitudes, so a strong match stays visibly
   * stronger than a weak one and {@code minScore} keeps meaning what it meant, at the cost of
   * assuming a cosine and a normalised BM25 are on comparable scales when they are not.
   */
  BLEND,

  /**
   * Reciprocal rank fusion: each list contributes {@code 1 / (k + rank)}. Uses only positions, so
   * it needs no assumption about scale, which is what makes it the usual choice for combining
   * rankings from different systems. In exchange it discards how far ahead of second place a first
   * place was.
   */
  RRF;

  /**
   * @throws IllegalArgumentException naming the accepted values, so a typo fails at startup instead
   *     of quietly selecting the default and reporting scores from a method nobody chose
   */
  public static FusionMethod from(String value) {
    String name = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    return Arrays.stream(values())
        .filter(method -> method.name().equals(name))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unknown search.fusion '"
                        + value
                        + "'. Expected one of "
                        + Arrays.stream(values())
                            .map(m -> m.name().toLowerCase(Locale.ROOT))
                            .collect(Collectors.joining(", "))
                        + "."));
  }
}
