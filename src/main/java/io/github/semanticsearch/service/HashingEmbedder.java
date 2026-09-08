package io.github.semanticsearch.service;

import java.util.HashMap;
import java.util.Map;

/**
 * Deterministic local text embedder built on feature hashing over word tokens and character
 * n-grams.
 *
 * <p>This is a <em>lexical</em> model, not a trained semantic one. Texts that share words score
 * highly, and morphological variants stay close because they share character n-grams ("rank",
 * "ranking" and "ranked" all overlap). It does not know that "car" and "automobile" mean the same
 * thing. That requires a trained model, which is what the other providers are for.
 *
 * <p>It exists so the service runs offline with no API key and still produces reproducible,
 * sensibly ordered results. Feature hashing is what makes the similarity graded: two texts sharing
 * some but not all features land at an intermediate distance, so scores spread across the range
 * instead of collapsing to "identical" or "unrelated".
 *
 * <p>Vectors are L2-normalised, so cosine similarity reduces to a dot product.
 */
public final class HashingEmbedder implements TextEmbedder {

  /**
   * Identifies the feature-extraction scheme below. Any change to tokenization, n-gram range or
   * weighting produces different vectors for the same text, so this is part of the embedding cache
   * key: bump it and cached vectors from the old scheme are no longer reachable.
   */
  public static final String ALGORITHM_VERSION = "hash-v1";

  private static final int MIN_NGRAM = 3;
  private static final int MAX_NGRAM = 5;

  /**
   * Character n-grams are weighted below whole-word matches so that an exact word match always
   * contributes more than a partial overlap.
   */
  private static final double NGRAM_WEIGHT = 0.4;

  private final int dimensions;

  public HashingEmbedder(int dimensions) {
    if (dimensions < 16) {
      throw new IllegalArgumentException(
          "Embedding dimensions must be at least 16, got " + dimensions);
    }
    this.dimensions = dimensions;
  }

  @Override
  public int dimensions() {
    return dimensions;
  }

  @Override
  public String modelId() {
    return "hashing/" + ALGORITHM_VERSION;
  }

  /**
   * Embed text into a unit-length vector.
   *
   * @param text text to embed; null or blank input yields a zero vector
   * @return a vector of length {@link #dimensions()}
   */
  @Override
  public double[] embed(String text) {
    double[] vector = new double[dimensions];
    if (text == null || text.isBlank()) {
      return vector;
    }

    Map<String, Integer> counts = countFeatures(text);
    for (Map.Entry<String, Integer> feature : counts.entrySet()) {
      String key = feature.getKey();
      int primary = mix(key.hashCode());
      int secondary = mix(key.hashCode() ^ 0x9e3779b9);

      int index = Math.floorMod(primary, dimensions);
      // A sign drawn from an independent hash keeps collisions from
      // systematically reinforcing each other.
      double sign = (secondary & 1) == 0 ? 1.0 : -1.0;
      // Sublinear term frequency: a word repeated ten times should not count ten
      // times as much as a word seen once. The feature-type weight multiplies
      // that result rather than being folded into the count, so an exact word
      // match always outweighs an n-gram match.
      double termFrequency = 1.0 + Math.log(feature.getValue());
      double typeWeight = key.charAt(0) == 'g' ? NGRAM_WEIGHT : 1.0;
      vector[index] += sign * typeWeight * termFrequency;
    }

    return normalise(vector);
  }

  private Map<String, Integer> countFeatures(String text) {
    Map<String, Integer> counts = new HashMap<>();
    // Shares Tokenizer with BM25 so both score the same terms, and so stop words
    // are excluded here too, since they would otherwise dominate the vector for a
    // natural-language query.
    for (String token : Tokenizer.tokenize(text)) {
      counts.merge("w:" + token, 1, Integer::sum);

      // Boundary markers let prefixes and suffixes act as distinct features, so
      // "ranking" is closer to "ranked" than to "outranking".
      String padded = '^' + token + '$';
      for (int n = MIN_NGRAM; n <= MAX_NGRAM; n++) {
        for (int start = 0; start + n <= padded.length(); start++) {
          counts.merge("g:" + padded.substring(start, start + n), 1, Integer::sum);
        }
      }
    }
    return counts;
  }

  private static double[] normalise(double[] vector) {
    double sumOfSquares = 0.0;
    for (double value : vector) {
      sumOfSquares += value * value;
    }
    if (sumOfSquares == 0.0) {
      return vector;
    }
    double norm = Math.sqrt(sumOfSquares);
    for (int i = 0; i < vector.length; i++) {
      vector[i] /= norm;
    }
    return vector;
  }

  /**
   * MurmurHash3 finaliser. {@link String#hashCode()} is specified by the language, so seeding from
   * it keeps embeddings stable across JVMs and releases, but its low bits are poorly distributed;
   * this avalanches them.
   */
  private static int mix(int value) {
    int h = value;
    h ^= h >>> 16;
    h *= 0x85ebca6b;
    h ^= h >>> 13;
    h *= 0xc2b2ae35;
    h ^= h >>> 16;
    return h;
  }
}
