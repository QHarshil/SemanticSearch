package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
class EvalServiceIntegrationTest {

  @Autowired private SeedService seedService;
  @Autowired private EvalService evalService;
  @Autowired private DocumentRepository documentRepository;

  @Test
  void evalProducesMetricsAndReport() throws IOException {
    seedService.seedDemoDocuments();
    List<EvalService.EvalQuery> gold =
        List.of(
            new EvalService.EvalQuery(
                "vector search embeddings", List.of(findByTitle("Vector Search Basics"))),
            new EvalService.EvalQuery(
                "ranking signals metadata boosts", List.of(findByTitle("Ranking Signals"))),
            new EvalService.EvalQuery("latency budget p95", List.of(findByTitle("Latency Budgets"))));

    EvalService.EvalResult result = evalService.runEval(gold, 5);
    assertTrue(result.mrr() >= 0.0);
    assertTrue(result.ndcg() >= 0.0);
    assertTrue(result.recallAtK() >= 0.0);

    File out = new File("target/eval/report.json");
    out.getParentFile().mkdirs();
    ObjectMapper mapper = new ObjectMapper();
    mapper.writeValue(
        out,
        Map.of(
            "mrr", result.mrr(),
            "ndcg", result.ndcg(),
            "recall", result.recallAtK(),
            "queries", result.details()));
    assertTrue(out.exists());
    assertTrue(Files.size(out.toPath()) > 0);
  }

  private java.util.UUID findByTitle(String title) {
    return documentRepository
        .findByTitle(title)
        .map(Document::getId)
        .orElseThrow(() -> new IllegalStateException("Expected seed doc not found: " + title));
  }
}
