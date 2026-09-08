package io.github.semanticsearch.service;

import java.nio.LongBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/**
 * Sentence embeddings from a transformer running locally through ONNX Runtime.
 *
 * <p>This is the semantic provider: it places "car" and "automobile" close together because the
 * model learned they are used the same way, which no amount of string overlap can recover. {@link
 * HashingEmbedder} matches shared words and character n-grams and is the zero-download default;
 * this one costs a model file and roughly a millisecond per embedding.
 *
 * <p>The three steps after inference are what turn token vectors into a sentence vector, and they
 * have to match how the model was trained or the vectors are subtly wrong rather than obviously
 * broken. Token vectors are averaged over the attention mask, so padding contributes nothing, and
 * the average is L2-normalised so cosine similarity is a dot product.
 *
 * <p>Safe for concurrent use: {@code OrtSession.run} and {@code HuggingFaceTokenizer.encode} are
 * both thread-safe, and nothing here keeps per-request state.
 */
public final class OnnxEmbedder implements TextEmbedder, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(OnnxEmbedder.class);

  private static final String INPUT_IDS = "input_ids";
  private static final String ATTENTION_MASK = "attention_mask";
  private static final String TOKEN_TYPE_IDS = "token_type_ids";

  private final String modelId;
  private final OrtEnvironment environment;
  private final OrtSession session;
  private final HuggingFaceTokenizer tokenizer;
  private final int dimensions;

  /**
   * @param modelId identifies the model in embedding cache keys
   * @param modelPath the ONNX graph
   * @param tokenizerPath the {@code tokenizer.json} the model was trained with
   * @param maxSequenceLength tokens past this point are dropped
   */
  public OnnxEmbedder(String modelId, Path modelPath, Path tokenizerPath, int maxSequenceLength) {
    this.modelId = modelId;
    try {
      this.environment = OrtEnvironment.getEnvironment();
      this.session =
          environment.createSession(modelPath.toString(), new OrtSession.SessionOptions());
      this.tokenizer =
          HuggingFaceTokenizer.builder()
              .optTokenizerPath(tokenizerPath)
              .optMaxLength(maxSequenceLength)
              .optTruncation(true)
              .build();
      this.dimensions = hiddenSize();
    } catch (OrtException e) {
      throw new IllegalStateException("Could not load the ONNX model at " + modelPath, e);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Could not load the tokenizer at " + tokenizerPath, e);
    }
    log.info("Loaded {} from {}, {} dimensions", modelId, modelPath, dimensions);
  }

  @Override
  public int dimensions() {
    return dimensions;
  }

  @Override
  public String modelId() {
    return modelId;
  }

  @Override
  public double[] embed(String text) {
    if (text == null || text.isBlank()) {
      return new double[dimensions];
    }

    Encoding encoding = tokenizer.encode(text);
    long[] ids = encoding.getIds();
    long[] mask = encoding.getAttentionMask();
    long[] types = encoding.getTypeIds();
    long[] shape = {1, ids.length};

    Map<String, OnnxTensor> inputs = new HashMap<>();
    try {
      inputs.put(INPUT_IDS, OnnxTensor.createTensor(environment, LongBuffer.wrap(ids), shape));
      inputs.put(
          ATTENTION_MASK, OnnxTensor.createTensor(environment, LongBuffer.wrap(mask), shape));
      inputs.put(
          TOKEN_TYPE_IDS, OnnxTensor.createTensor(environment, LongBuffer.wrap(types), shape));
      try (OrtSession.Result result = session.run(inputs)) {
        float[][][] hiddenStates = (float[][][]) result.get(0).getValue();
        return normalise(meanPool(hiddenStates[0], mask));
      }
    } catch (OrtException e) {
      throw new IllegalStateException("Inference failed for " + modelId, e);
    } finally {
      inputs.values().forEach(OnnxTensor::close);
    }
  }

  /**
   * Average the token vectors the model produced, counting only the positions the attention mask
   * marks as real. Taking the first token instead would read the CLS position, which this model was
   * not trained to use as a sentence vector.
   */
  private double[] meanPool(float[][] tokenVectors, long[] mask) {
    double[] pooled = new double[dimensions];
    int counted = 0;
    for (int token = 0; token < tokenVectors.length; token++) {
      if (mask[token] == 0) {
        continue;
      }
      counted++;
      for (int d = 0; d < dimensions; d++) {
        pooled[d] += tokenVectors[token][d];
      }
    }
    if (counted == 0) {
      return pooled;
    }
    for (int d = 0; d < dimensions; d++) {
      pooled[d] /= counted;
    }
    return pooled;
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

  /** Reads the vector width from the graph, so a different model does not need a config change. */
  private int hiddenSize() throws OrtException {
    var info = session.getOutputInfo().values().iterator().next().getInfo();
    if (!(info instanceof ai.onnxruntime.TensorInfo tensorInfo)) {
      throw new IllegalStateException("Expected the model to output a tensor, got " + info);
    }
    long[] shape = tensorInfo.getShape();
    if (shape.length != 3 || shape[2] <= 0) {
      throw new IllegalStateException(
          "Expected an output shaped [batch, tokens, hidden] with a fixed hidden size, got "
              + java.util.Arrays.toString(shape));
    }
    return (int) shape[2];
  }

  @Override
  public void close() {
    tokenizer.close();
    try {
      session.close();
    } catch (OrtException e) {
      log.warn("Could not close the ONNX session for {}", modelId, e);
    }
  }
}
