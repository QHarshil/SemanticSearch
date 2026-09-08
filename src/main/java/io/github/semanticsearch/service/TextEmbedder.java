package io.github.semanticsearch.service;

/**
 * An embedding model that runs inside this process.
 *
 * <p>Implementations are used interchangeably by {@link EmbeddingService}, so they have to agree on
 * two things. Every vector is {@link #dimensions()} long, because the search index mapping is built
 * from that number. Every vector is L2-normalised, because cosine similarity is computed as a dot
 * product and an unnormalised vector would score by magnitude as much as by direction.
 */
public interface TextEmbedder {

  /**
   * Embed text into a unit-length vector. Null or blank input yields a zero vector, which scores
   * zero against everything.
   */
  double[] embed(String text);

  /** Width of every vector this embedder produces. */
  int dimensions();

  /**
   * Identifies the model and the feature scheme, and forms part of the embedding cache key.
   *
   * <p>Two embedders that produce different vectors for the same text must return different ids.
   * Vectors from different models occupy unrelated coordinate spaces, so a shared cache entry would
   * hand one model's vector to another and score it against an index built by neither.
   */
  String modelId();
}
