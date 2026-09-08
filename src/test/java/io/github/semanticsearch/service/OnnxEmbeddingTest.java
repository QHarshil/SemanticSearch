package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Covers the one thing the ONNX provider exists for: similarity that follows meaning instead of
 * spelling.
 *
 * <p>The numbers in the assertions are cosine similarities measured from this model, quoted in the
 * comment beside each. They are asserted as inequalities with room to spare, because a threshold
 * pinned to four decimal places would break on an ONNX Runtime release that reorders a floating
 * point reduction while the ranking stays identical.
 *
 * <p>Requires the model. It is around ninety megabytes and is downloaded to {@code
 * ~/.cache/semantic-search-java/models} on first use, then reused.
 */
@SpringBootTest(properties = "embedding.provider=onnx")
@ActiveProfiles("test")
class OnnxEmbeddingTest {

  @Autowired private TextEmbedder embedder;
  @Autowired private EmbeddingService embeddingService;

  @Test
  void scoresSynonymsCloseTogetherAndUnrelatedWordsApart() {
    // Measured: 0.86 against 0.39. HashingEmbedder scores the same pair at -0.12,
    // because "car" and "automobile" share no word and no character trigram, so
    // what is left is hash collision noise. No lexical model can do better.
    double synonyms = similarity("car", "automobile");
    double unrelated = similarity("car", "banana");

    assertTrue(synonyms > 0.8, "car/automobile scored " + synonyms);
    assertTrue(unrelated < 0.5, "car/banana scored " + unrelated);
  }

  @Test
  void rankstheRightDocumentFirstForAQueryThatSharesNoContentWords() {
    // The gold query the lexical embedder misses entirely. Against "Ranking
    // Signals" it shares only stopwords, so the only route to the right document
    // is meaning. Measured: 0.26 against 0.18.
    String query = "what affects result ordering";
    String ranking =
        "Ranking Signals\nRanking blends relevance signals such as semantic similarity, "
            + "lexical overlap and metadata boosts.";
    String latency = "Latency Budgets\nLatency budgets keep search responses under a target p95.";

    assertTrue(
        similarity(query, ranking) > similarity(query, latency),
        "the ranking document scored "
            + similarity(query, ranking)
            + " and the latency document "
            + similarity(query, latency));
  }

  @Test
  void producesUnitLengthVectorsSoCosineSimilarityIsADotProduct() {
    double[] vector = embedder.embed("vector search finds similar documents");

    assertEquals(384, vector.length);
    assertEquals(1.0, Math.sqrt(dot(vector, vector)), 1e-6);
  }

  @Test
  void embedsBlankTextAsAZeroVectorRatherThanFailing() {
    assertArrayEquals(new double[384], embedder.embed("   "));
  }

  @Test
  void isDeterministic() {
    assertArrayEquals(
        embedder.embed("ranking signals metadata boosts"),
        embedder.embed("ranking signals metadata boosts"),
        0.0);
  }

  @Test
  void reportsTheModelsOwnWidthRatherThanTheConfiguredOne() {
    // application-test.yml asks for 128 dimensions. The model produces 384, and
    // the index mapping is built from what the service reports, so believing the
    // property here would reject every vector the model produces.
    assertEquals(384, embeddingService.dimensions());
    assertEquals(384, embeddingService.embed("any text at all").size());
  }

  @Test
  void namesTheModelInTheCacheNamespaceSoItsVectorsAreNotServedToAnotherProvider() {
    assertTrue(
        embeddingService.cacheNamespace().startsWith("onnx/all-MiniLM-L6-v2"),
        embeddingService.cacheNamespace());
  }

  private double similarity(String left, String right) {
    return dot(embedder.embed(left), embedder.embed(right));
  }

  private static double dot(double[] left, double[] right) {
    double sum = 0.0;
    for (int i = 0; i < left.length; i++) {
      sum += left[i] * right[i];
    }
    return sum;
  }
}
