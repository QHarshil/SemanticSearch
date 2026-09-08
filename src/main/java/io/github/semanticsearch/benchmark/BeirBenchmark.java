package io.github.semanticsearch.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.cache.CacheManager;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.semanticsearch.benchmark.BeirDataset.BeirDocument;
import io.github.semanticsearch.benchmark.BeirDataset.BeirQuery;
import io.github.semanticsearch.config.SearchProperties;
import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.model.SearchRequest;
import io.github.semanticsearch.model.SearchResult;
import io.github.semanticsearch.repository.DocumentRepository;
import io.github.semanticsearch.service.DocumentService;
import io.github.semanticsearch.service.EmbeddingService;
import io.github.semanticsearch.service.LexicalIndex;
import io.github.semanticsearch.service.RankingMetrics;
import io.github.semanticsearch.service.SearchService;
import io.github.semanticsearch.service.VerifiedFileCache;

/**
 * Scores this service's retrieval against a published BEIR dataset and writes a report.
 *
 * <p>The eight-query gold set in {@code EvalService} guards against regressions on a corpus too
 * small to say whether the ranking is good. SciFact is 5,183 documents and 300 judged queries, and
 * BEIR publishes numbers for the same split that other systems are measured against.
 *
 * <p>It runs under the {@code benchmark} profile and exits when it finishes, because indexing five
 * thousand documents and answering three hundred queries takes minutes and has no place in the test
 * suite. The corpus is loaded once and scored four ways, so the four rows differ only in how
 * retrieval was configured.
 *
 * <pre>
 * EMBEDDING_PROVIDER=onnx java -jar target/semantic-search-java-1.0.0.jar \
 *   --spring.profiles.active=benchmark
 * </pre>
 */
@Component
@Profile("benchmark")
public class BeirBenchmark implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(BeirBenchmark.class);

  private final BenchmarkProperties properties;
  private final DocumentService documentService;
  private final DocumentRepository documentRepository;
  private final SearchService searchService;
  private final LexicalIndex lexicalIndex;
  private final EmbeddingService embeddingService;
  private final SearchProperties searchProperties;
  private final CacheManager cacheManager;
  private final ConfigurableApplicationContext context;
  private final ObjectMapper objectMapper;

  public BeirBenchmark(
      BenchmarkProperties properties,
      DocumentService documentService,
      DocumentRepository documentRepository,
      SearchService searchService,
      LexicalIndex lexicalIndex,
      EmbeddingService embeddingService,
      SearchProperties searchProperties,
      CacheManager cacheManager,
      ConfigurableApplicationContext context,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.documentService = documentService;
    this.documentRepository = documentRepository;
    this.searchService = searchService;
    this.lexicalIndex = lexicalIndex;
    this.embeddingService = embeddingService;
    this.searchProperties = searchProperties;
    this.cacheManager = cacheManager;
    this.context = context;
    this.objectMapper = objectMapper;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    execute();
    // Nothing here serves traffic, so holding the port open after the report is
    // written would leave the command looking like it had hung.
    System.exit(SpringApplication.exit(context, () -> 0));
  }

  /**
   * Indexes the dataset, scores it four ways and writes the report.
   *
   * <p>Separate from {@link #run} so a test can drive the whole thing over a fixture without the
   * process exiting underneath it.
   *
   * @return the report, as written
   */
  public Map<String, Object> execute() throws IOException {
    BeirDataset dataset = loadDataset();
    log.info(
        "Loaded {}: {} documents, {} judged queries",
        dataset.name(),
        dataset.corpus().size(),
        dataset.queries().size());

    Instant indexingStarted = Instant.now();
    Map<String, UUID> byCorpusId = index(dataset.corpus());
    Duration indexing = Duration.between(indexingStarted, Instant.now());
    log.info("Indexed {} documents in {}s", byCorpusId.size(), indexing.toSeconds());

    List<Map<String, Object>> runs = new ArrayList<>();
    runs.add(score(dataset, byCorpusId, "bm25 only", this::lexicalRanking));
    runs.add(configured(dataset, byCorpusId, "vector only", false, "blend"));
    runs.add(configured(dataset, byCorpusId, "hybrid, blend", true, "blend"));
    runs.add(configured(dataset, byCorpusId, "hybrid, rrf", true, "rrf"));

    Map<String, Object> report = report(dataset, indexing, runs);
    write(report);
    return report;
  }

  private Map<String, Object> configured(
      BeirDataset dataset,
      Map<String, UUID> byCorpusId,
      String label,
      boolean hybrid,
      String fusion) {
    searchProperties.setHybridEnabled(hybrid);
    searchProperties.setFusion(fusion);
    return score(dataset, byCorpusId, label, this::pipelineRanking);
  }

  /** Runs every query through one retrieval configuration and averages the metrics. */
  private Map<String, Object> score(
      BeirDataset dataset, Map<String, UUID> byCorpusId, String label, Ranker ranker) {
    clearCaches();
    int k = properties.getK();
    int recallK = properties.getRecallK();

    double ndcg = 0.0;
    double recall = 0.0;
    double reciprocalRank = 0.0;
    List<Long> latencies = new ArrayList<>(dataset.queries().size());

    for (BeirQuery query : dataset.queries()) {
      Set<String> relevantCorpusIds = dataset.relevant().get(query.id());
      List<UUID> gold = new ArrayList<>();
      for (String corpusId : relevantCorpusIds) {
        UUID id = byCorpusId.get(corpusId);
        if (id == null) {
          throw new IllegalStateException(
              "qrels judge corpus document " + corpusId + ", which is not in corpus.jsonl");
        }
        gold.add(id);
      }

      long started = System.nanoTime();
      List<UUID> ranked = ranker.rank(query.text(), recallK);
      latencies.add(System.nanoTime() - started);

      ndcg += RankingMetrics.ndcgAt(ranked, gold, k);
      recall += RankingMetrics.recallAt(ranked, gold, recallK);
      reciprocalRank += RankingMetrics.reciprocalRank(ranked, gold);
    }

    int queries = dataset.queries().size();
    Map<String, Object> run = new LinkedHashMap<>();
    run.put("configuration", label);
    run.put("ndcgAt" + k, round(ndcg / queries));
    run.put("recallAt" + recallK, round(recall / queries));
    run.put("mrr", round(reciprocalRank / queries));
    run.put("medianQueryMs", round(percentile(latencies, 0.5)));
    run.put("p95QueryMs", round(percentile(latencies, 0.95)));
    log.info("{}: {}", label, run);
    return run;
  }

  /** The full pipeline, exactly as the API serves it. */
  private List<UUID> pipelineRanking(String query, int limit) {
    SearchRequest request =
        SearchRequest.builder()
            .query(query)
            .limit(limit)
            // No floor. A benchmark measures ranking, and a threshold tuned for
            // one embedder would silently truncate recall for another.
            .minScore(0.0)
            .includeContent(false)
            .includeHighlights(false)
            .build();
    return searchService.search(request).stream().map(SearchResult::getId).toList();
  }

  /** BM25 alone, straight from the inverted index, as the baseline the vector side has to beat. */
  private List<UUID> lexicalRanking(String query, int limit) {
    return lexicalIndex.search(query, limit).stream().map(Map.Entry::getKey).toList();
  }

  private Map<String, UUID> index(List<BeirDocument> corpus) {
    Map<String, UUID> byCorpusId = new HashMap<>(corpus.size());
    int written = 0;
    for (BeirDocument source : corpus) {
      Document document = new Document();
      document.setTitle(source.title().isBlank() ? source.id() : source.title());
      document.setContent(source.text());
      document.setMetadata(Map.of("corpus_id", source.id()));
      documentService
          .createIfAbsent(document)
          .ifPresent(saved -> byCorpusId.put(source.id(), saved.getId()));
      if (++written % 1000 == 0) {
        log.info("Indexed {} of {}", written, corpus.size());
      }
    }
    if (byCorpusId.size() != corpus.size()) {
      // createIfAbsent drops exact duplicates, and BEIR corpora do contain them.
      // A warning is enough here. The qrels lookup in score() is what turns a
      // missing document into an error, and only when that document is judged.
      log.warn(
          "Indexed {} of {} documents; the rest were exact content duplicates",
          byCorpusId.size(),
          corpus.size());
    }
    return byCorpusId;
  }

  /**
   * The dataset directory, unpacking the archive first if it is not already there.
   *
   * <p>An unpacked directory is enough on its own, so pointing {@code benchmark.dataset-dir} at one
   * needs no archive and no network. Fetching and verifying the zip only to ignore it would make an
   * offline run impossible for no gain.
   */
  private BeirDataset loadDataset() throws IOException {
    Path root = properties.getDatasetDir() != null ? properties.getDatasetDir() : defaultDir();
    Path extracted = root.resolve(properties.getDataset());

    if (!Files.isDirectory(extracted)) {
      VerifiedFileCache cache =
          new VerifiedFileCache(root, properties.isAutoDownload(), "benchmark.auto-download");
      Path archive =
          cache.resolve(
              properties.getDataset() + ".zip",
              properties.getDatasetUrl(),
              properties.getDatasetSha256());
      BeirDataset.unzip(archive, root);
    }
    return BeirDataset.load(extracted, properties.getSplit());
  }

  private static Path defaultDir() {
    return Path.of(System.getProperty("user.home"), ".cache", "semantic-search-java", "datasets");
  }

  private Map<String, Object> report(
      BeirDataset dataset, Duration indexing, List<Map<String, Object>> runs) {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("dataset", "beir/" + dataset.name());
    report.put("split", properties.getSplit());
    report.put("documents", documentRepository.count());
    report.put("queries", dataset.queries().size());
    report.put("embeddingProvider", embeddingService.cacheNamespace());
    report.put("dimensions", embeddingService.dimensions());
    report.put("vectorWeight", searchProperties.getHybridVectorWeight());
    report.put("rrfK", searchProperties.getRrfK());
    report.put("indexingSeconds", indexing.toSeconds());
    report.put("runs", runs);
    return report;
  }

  private void write(Map<String, Object> report) throws IOException {
    Path output = properties.getOutput();
    if (output.getParent() != null) {
      Files.createDirectories(output.getParent());
    }
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
    log.info("Wrote {}", output.toAbsolutePath());
  }

  private void clearCaches() {
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
  }

  private static double percentile(List<Long> nanos, double fraction) {
    List<Long> sorted = new ArrayList<>(nanos);
    sorted.sort(Long::compare);
    int index = Math.min(sorted.size() - 1, (int) Math.ceil(fraction * sorted.size()) - 1);
    return sorted.get(Math.max(0, index)) / 1_000_000.0;
  }

  private static double round(double value) {
    return Math.round(value * 10_000) / 10_000.0;
  }

  @FunctionalInterface
  private interface Ranker {
    List<UUID> rank(String query, int limit);
  }
}
