package io.github.semanticsearch.config;

import java.net.URI;
import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the local transformer comes from and where it is kept. See {@code embedding.onnx} in
 * application.yml.
 *
 * <p>The weights are around ninety megabytes, so they are fetched on first use instead of being
 * committed. Both the source and the expected digest are configuration, so pointing the service at
 * an internal mirror or a different sentence-transformer is a config change rather than a code
 * change.
 */
@ConfigurationProperties(prefix = "embedding.onnx")
public class OnnxProperties {

  /** Name the model is recorded under in embedding cache keys. */
  private String modelId = "onnx/all-MiniLM-L6-v2";

  /** Directory holding the downloaded model and tokenizer. */
  private Path modelDir;

  /** Fetch the files when they are not already in {@link #getModelDir()}. */
  private boolean autoDownload = true;

  private URI modelUrl;

  private String modelSha256;

  private URI tokenizerUrl;

  private String tokenizerSha256;

  /**
   * Tokens beyond this position are dropped. The model was trained at 256, and attention cost grows
   * with the square of the sequence, so a longer window costs more than it recovers.
   */
  private int maxSequenceLength = 256;

  public String getModelId() {
    return modelId;
  }

  public void setModelId(String modelId) {
    this.modelId = modelId;
  }

  public Path getModelDir() {
    return modelDir;
  }

  public void setModelDir(Path modelDir) {
    this.modelDir = modelDir;
  }

  public boolean isAutoDownload() {
    return autoDownload;
  }

  public void setAutoDownload(boolean autoDownload) {
    this.autoDownload = autoDownload;
  }

  public URI getModelUrl() {
    return modelUrl;
  }

  public void setModelUrl(URI modelUrl) {
    this.modelUrl = modelUrl;
  }

  public String getModelSha256() {
    return modelSha256;
  }

  public void setModelSha256(String modelSha256) {
    this.modelSha256 = modelSha256;
  }

  public URI getTokenizerUrl() {
    return tokenizerUrl;
  }

  public void setTokenizerUrl(URI tokenizerUrl) {
    this.tokenizerUrl = tokenizerUrl;
  }

  public String getTokenizerSha256() {
    return tokenizerSha256;
  }

  public void setTokenizerSha256(String tokenizerSha256) {
    this.tokenizerSha256 = tokenizerSha256;
  }

  public int getMaxSequenceLength() {
    return maxSequenceLength;
  }

  public void setMaxSequenceLength(int maxSequenceLength) {
    this.maxSequenceLength = maxSequenceLength;
  }
}
