package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * These tests pin the properties the ranking pipeline depends on: that similarity is
 * <em>graded</em> rather than all-or-nothing, and that vectors are unit length so cosine similarity
 * is a dot product.
 */
class HashingEmbedderTest {

  private static final int DIMENSIONS = 256;
  private final HashingEmbedder embedder = new HashingEmbedder(DIMENSIONS);

  @Test
  void producesUnitLengthVectorsOfTheConfiguredSize() {
    double[] vector = embedder.embed("vector search finds similar documents");

    assertEquals(DIMENSIONS, vector.length);
    assertEquals(1.0, norm(vector), 1e-9, "vectors must be L2-normalised");
  }

  @Test
  void isDeterministicAcrossInstances() {
    // A separate instance rules out any per-object state, and calling the class
    // directly rules out the Spring cache that sits in front of EmbeddingService.
    double[] first = embedder.embed("ranking signals metadata boosts");
    double[] second = new HashingEmbedder(DIMENSIONS).embed("ranking signals metadata boosts");

    assertArrayEquals(first, second, 0.0);
  }

  @Test
  void similarityIsGradedNotBinary() {
    double[] query = embedder.embed("vector search embeddings");

    double identical = cosine(query, embedder.embed("vector search embeddings"));
    double overlapping = cosine(query, embedder.embed("vector search finds similar documents"));
    double unrelated = cosine(query, embedder.embed("latency budgets keep responses fast"));

    assertEquals(1.0, identical, 1e-9, "identical text should score 1.0");
    assertTrue(
        overlapping > unrelated,
        "text sharing words must outrank unrelated text, but got overlapping="
            + overlapping
            + " unrelated="
            + unrelated);
    // Partial overlap must carry a clearly non-trivial signal rather than scoring
    // the same as no overlap, which is what any hash without input locality does.
    assertTrue(
        overlapping > 0.2,
        "partial word overlap must produce meaningful similarity, got " + overlapping);
    assertTrue(unrelated < 0.2, "unrelated text should score near zero, got " + unrelated);
  }

  @Test
  void morphologicalVariantsStayCloserThanUnrelatedWords() {
    // Character n-grams are the reason this holds; whole-word features alone
    // would treat "ranking" and "ranked" as unrelated tokens.
    double variant = cosine(embedder.embed("ranking"), embedder.embed("ranked"));
    double unrelated = cosine(embedder.embed("ranking"), embedder.embed("latency"));

    assertTrue(
        variant > unrelated,
        "shared word stems should score above unrelated words, got variant="
            + variant
            + " unrelated="
            + unrelated);
  }

  @Test
  void wordOrderDoesNotChangeTheVector() {
    // This is a bag-of-features model. Making the property explicit documents a
    // real limitation rather than leaving it to be discovered.
    assertArrayEquals(embedder.embed("ranking signals"), embedder.embed("signals ranking"), 1e-12);
  }

  @Test
  void repeatingTheWholeTextDoesNotChangeItsDirection() {
    // Sublinear weighting plus L2 normalisation makes the vector scale-invariant:
    // every feature count rises together, so only the direction matters.
    double similarity = cosine(embedder.embed("search"), embedder.embed("search search search"));

    assertEquals(1.0, similarity, 1e-9);
  }

  @Test
  void aRepeatedTermDoesNotCrowdOutTheRestOfTheDocument() {
    String alphaHeavyDocument = "alpha alpha alpha alpha alpha alpha alpha alpha beta";

    double alphaBalanced = cosine(embedder.embed("alpha"), embedder.embed("alpha beta"));
    double alphaHeavy = cosine(embedder.embed("alpha"), embedder.embed(alphaHeavyDocument));
    double betaHeavy = cosine(embedder.embed("beta"), embedder.embed(alphaHeavyDocument));

    assertTrue(
        alphaHeavy > alphaBalanced,
        "a document mentioning the query term more often should rank higher, got heavy="
            + alphaHeavy
            + " balanced="
            + alphaBalanced);
    // The point of sublinear weighting: even after eight repetitions of "alpha",
    // "beta" is still retrievable. Under linear term frequency "alpha" would take
    // over the vector and this would collapse towards zero.
    assertTrue(
        betaHeavy > 0.15,
        "a term mentioned once must stay findable in a document dominated by another term, got "
            + betaHeavy);
  }

  @Test
  void blankInputYieldsZeroVector() {
    assertEquals(0.0, norm(embedder.embed("")), 0.0);
    assertEquals(0.0, norm(embedder.embed("   ")), 0.0);
    assertEquals(0.0, norm(embedder.embed(null)), 0.0);
  }

  @Test
  void rejectsDimensionsTooSmallToBeUseful() {
    assertThrows(IllegalArgumentException.class, () -> new HashingEmbedder(8));
  }

  private static double norm(double[] vector) {
    double sum = 0.0;
    for (double value : vector) {
      sum += value * value;
    }
    return Math.sqrt(sum);
  }

  private static double cosine(double[] a, double[] b) {
    double dot = 0.0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
    }
    return dot;
  }
}
