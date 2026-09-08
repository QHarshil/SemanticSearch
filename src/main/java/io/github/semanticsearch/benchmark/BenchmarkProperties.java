package io.github.semanticsearch.benchmark;

import java.net.URI;
import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the benchmark corpus comes from and how it is scored. See {@code benchmark} in
 * application.yml.
 */
@ConfigurationProperties(prefix = "benchmark")
public class BenchmarkProperties {

  /** Dataset directory name inside the archive, and the label used in the report. */
  private String dataset = "scifact";

  /** Which qrels split to score against. */
  private String split = "test.tsv";

  private URI datasetUrl;

  private String datasetSha256;

  /** Where the archive and its contents are kept. Defaults under the user's cache directory. */
  private Path datasetDir;

  private boolean autoDownload = true;

  /** The k in NDCG@k. Ten is what BEIR reports, so results can be read beside published ones. */
  private int k = 10;

  /** The k in Recall@k, which BEIR reports at 100. */
  private int recallK = 100;

  /** Where the report is written. */
  private Path output = Path.of("docs", "benchmark-scifact.json");

  public String getDataset() {
    return dataset;
  }

  public void setDataset(String dataset) {
    this.dataset = dataset;
  }

  public String getSplit() {
    return split;
  }

  public void setSplit(String split) {
    this.split = split;
  }

  public URI getDatasetUrl() {
    return datasetUrl;
  }

  public void setDatasetUrl(URI datasetUrl) {
    this.datasetUrl = datasetUrl;
  }

  public String getDatasetSha256() {
    return datasetSha256;
  }

  public void setDatasetSha256(String datasetSha256) {
    this.datasetSha256 = datasetSha256;
  }

  public Path getDatasetDir() {
    return datasetDir;
  }

  public void setDatasetDir(Path datasetDir) {
    this.datasetDir = datasetDir;
  }

  public boolean isAutoDownload() {
    return autoDownload;
  }

  public void setAutoDownload(boolean autoDownload) {
    this.autoDownload = autoDownload;
  }

  public int getK() {
    return k;
  }

  public void setK(int k) {
    this.k = k;
  }

  public int getRecallK() {
    return recallK;
  }

  public void setRecallK(int recallK) {
    this.recallK = recallK;
  }

  public Path getOutput() {
    return output;
  }

  public void setOutput(Path output) {
    this.output = output;
  }
}
