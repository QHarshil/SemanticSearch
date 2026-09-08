package io.github.semanticsearch.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.semanticsearch.config.SearchProperties;
import io.github.semanticsearch.repository.DocumentRepository;
import io.github.semanticsearch.service.DocumentService;
import io.github.semanticsearch.service.EmbeddingService;
import io.github.semanticsearch.service.IndexService;
import io.github.semanticsearch.service.LexicalIndex;
import io.github.semanticsearch.service.SearchService;

/**
 * Drives the whole benchmark over a corpus small enough to reason about.
 *
 * <p>Every number this repository quotes about ranking quality on a real dataset comes out of this
 * class, so its plumbing is worth checking against answers known in advance. The three documents
 * are on unrelated subjects with one relevant per query, which makes a clean sweep the expected
 * result and any shortfall a defect in the harness instead of a hard ranking problem.
 *
 * <p>The runner is built here rather than autowired. It is {@code @Profile("benchmark")}, and
 * activating that profile would have Spring Boot execute it on context refresh, where it ends by
 * calling {@code System.exit}.
 */
@SpringBootTest
@ActiveProfiles("test")
class BeirBenchmarkTest {

  @TempDir Path datasetRoot;

  @Autowired private DocumentService documentService;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private SearchService searchService;
  @Autowired private LexicalIndex lexicalIndex;
  @Autowired private EmbeddingService embeddingService;
  @Autowired private SearchProperties searchProperties;
  @Autowired private CacheManager cacheManager;
  @Autowired private ConfigurableApplicationContext context;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private IndexService indexService;

  private BeirBenchmark benchmark;
  private Path reportPath;

  @BeforeEach
  void writeFixtureAndBuildRunner() throws IOException {
    documentRepository.deleteAll();
    indexService.rebuildIndex();
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());

    Path dataset = datasetRoot.resolve("tiny");
    Files.createDirectories(dataset.resolve("qrels"));
    Files.writeString(
        dataset.resolve("corpus.jsonl"),
        """
        {"_id": "d1", "title": "Sourdough", "text": "A long cold proof develops flavour in bread."}
        {"_id": "d2", "title": "Kubernetes", "text": "A pod is the smallest deployable unit in a cluster."}
        {"_id": "d3", "title": "Tides", "text": "The moon gravity raises the sea twice a day."}
        """,
        StandardCharsets.UTF_8);
    Files.writeString(
        dataset.resolve("queries.jsonl"),
        """
        {"_id": "q1", "text": "sourdough bread proof flavour"}
        {"_id": "q2", "text": "kubernetes pod cluster deployable"}
        {"_id": "q3", "text": "moon gravity sea tides"}
        """,
        StandardCharsets.UTF_8);
    Files.writeString(
        dataset.resolve("qrels").resolve("test.tsv"),
        """
        query-id\tcorpus-id\tscore
        q1\td1\t1
        q2\td2\t1
        q3\td3\t1
        """,
        StandardCharsets.UTF_8);

    reportPath = datasetRoot.resolve("report.json");
    BenchmarkProperties properties = new BenchmarkProperties();
    properties.setDataset("tiny");
    properties.setDatasetDir(datasetRoot);
    // No archive and no network. An unpacked directory is enough, and a run that
    // reached Hugging Face from the test suite would be a defect in itself.
    properties.setAutoDownload(false);
    properties.setK(3);
    properties.setRecallK(3);
    properties.setOutput(reportPath);

    benchmark =
        new BeirBenchmark(
            properties,
            documentService,
            documentRepository,
            searchService,
            lexicalIndex,
            embeddingService,
            searchProperties,
            cacheManager,
            context,
            objectMapper);
  }

  @Test
  void scoresEveryConfigurationOverTheJudgedQueries() throws IOException {
    Map<String, Object> report = benchmark.execute();

    assertEquals("beir/tiny", report.get("dataset"));
    assertEquals(3, report.get("queries"));
    assertEquals(3L, report.get("documents"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> runs = (List<Map<String, Object>>) report.get("runs");
    assertEquals(
        List.of("bm25 only", "vector only", "hybrid, blend", "hybrid, rrf"),
        runs.stream().map(run -> run.get("configuration")).toList());

    for (Map<String, Object> run : runs) {
      String where = String.valueOf(run.get("configuration"));
      assertEquals(1.0, (Double) run.get("ndcgAt3"), 1e-9, where + " NDCG@3");
      assertEquals(1.0, (Double) run.get("recallAt3"), 1e-9, where + " Recall@3");
      assertEquals(1.0, (Double) run.get("mrr"), 1e-9, where + " MRR");
    }
  }

  @Test
  void writesAReportCarryingTheConfigurationTheNumbersCameFrom() throws IOException {
    Map<String, Object> report = benchmark.execute();

    assertTrue(Files.exists(reportPath), "no report was written");
    var written = objectMapper.readTree(reportPath.toFile());
    assertEquals(report.get("dataset"), written.get("dataset").asText());
    assertEquals(4, written.get("runs").size());
    // A relevance figure means nothing without the model and width behind it, so
    // both travel with the numbers.
    assertTrue(written.get("embeddingProvider").asText().startsWith("hashing/"));
    assertEquals(128, written.get("dimensions").asInt());
  }

  @Test
  void restoresNothingItChanged() throws IOException {
    // execute() sweeps through four retrieval configurations by mutating the
    // shared SearchProperties, so a caller has to know it does not put them back.
    searchProperties.setHybridEnabled(true);
    searchProperties.setFusion("blend");

    benchmark.execute();

    assertEquals("rrf", searchProperties.getFusion(), "the last configuration scored was rrf");
    searchProperties.setFusion("blend");
  }
}
