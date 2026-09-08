package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.semanticsearch.repository.DocumentRepository;

/**
 * Relevance regression test over the seeded demo corpus.
 *
 * <p>The thresholds are real lower bounds. Asserting {@code mrr >= 0.0} would be vacuous, since all
 * three metrics are non-negative by construction and would hold even if search returned nothing for
 * every query.
 *
 * <p>These thresholds are set below current measured performance so ordinary tuning does not break
 * the build, but far enough above chance that a real regression will.
 */
@SpringBootTest
@ActiveProfiles("test")
class EvalServiceIntegrationTest {

  // Measured here with the lexical embedder at the 128 dimensions this profile
  // configures: MRR 0.646, NDCG@5 0.704, Recall@5 0.875, with 7 of the 8 gold
  // documents retrieved and 4 ranked first. The README quotes 0.615 / 0.679 for
  // the same corpus, which is the demo profile at 256 dimensions. The gold queries
  // are natural-language paraphrases rather than restatements of the document
  // text, which is deliberately hard for a lexical model. It can match "term
  // frequency scoring" to the BM25 document, but not "what affects result
  // ordering" to "Ranking Signals". A hosted embedding model should score
  // higher; if you switch providers, re-measure and raise these.
  //
  // Thresholds sit below the measured values so ordinary tuning does not break
  // the build, and far enough above chance (random ordering over eight
  // documents gives roughly 0.2 MRR) that a real regression will.
  private static final double MIN_MRR = 0.55;

  private static final double MIN_NDCG = 0.60;
  private static final double MIN_RECALL = 0.80;
  private static final int MIN_QUERIES_WITH_A_HIT = 7;

  @Autowired private SeedService seedService;
  @Autowired private EvalService evalService;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private IndexService indexService;
  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void seedCorpus() {
    documentRepository.deleteAll();
    indexService.rebuildIndex();
    // Both caches key on query text, which is identical across these tests while
    // the document IDs behind it are not. Leaving entries in place would serve one
    // test's results to the next and report recall against IDs that no longer
    // exist.
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    seedService.seedDemoDocuments();
  }

  @Test
  void curatedGoldSetMeetsRelevanceThresholds() {
    // Exercises runCuratedEval, the method both EvalController and EvalConfig
    // call, instead of a gold set assembled here. A local fixture would leave
    // the production path uncovered.
    EvalService.EvalResult result = evalService.runCuratedEval(5);
    assertEquals(8, result.totalQueries());
    assertTrue(
        result.mrr() >= MIN_MRR,
        "MRR regressed to " + result.mrr() + ", expected at least " + MIN_MRR);
    assertTrue(
        result.ndcg() >= MIN_NDCG,
        "NDCG@5 regressed to " + result.ndcg() + ", expected at least " + MIN_NDCG);
    assertTrue(
        result.recallAtK() >= MIN_RECALL,
        "Recall@5 regressed to " + result.recallAtK() + ", expected at least " + MIN_RECALL);
  }

  @Test
  void almostEveryGoldQueryRetrievesItsDocument() {
    EvalService.EvalResult result = evalService.runCuratedEval(5);

    // Counted per query, not averaged. Several queries degrading a little and one
    // failing outright can produce the same mean, and only the per-query count
    // separates them.
    long queriesWithAHit = result.details().stream().filter(q -> q.recall() > 0.0).count();
    String misses =
        result.details().stream()
            .filter(q -> q.recall() == 0.0)
            .map(EvalService.QueryEval::query)
            .toList()
            .toString();

    assertTrue(
        queriesWithAHit >= MIN_QUERIES_WITH_A_HIT,
        "only "
            + queriesWithAHit
            + " of "
            + result.totalQueries()
            + " queries retrieved their gold document; missed "
            + misses);
  }

  @Test
  void writesTheReportCiPublishes() throws IOException {
    // CI uploads target/eval/report.json as an artifact. The EvalResult record is
    // serialised as-is, so the file matches what GET /api/v1/eval/run returns and
    // what EvalConfig writes at startup. A hand-built map here would be a third
    // schema for the same measurement.
    EvalService.EvalResult result = evalService.runCuratedEval(5);

    File out = new File("target/eval/report.json");
    out.getParentFile().mkdirs();
    ObjectMapper mapper = new ObjectMapper();
    mapper.writeValue(out, result);

    var written = mapper.readTree(out);
    assertTrue(Files.size(out.toPath()) > 0);
    assertEquals(result.mrr(), written.get("mrr").asDouble(), 1e-9);
    assertEquals(result.ndcg(), written.get("ndcg").asDouble(), 1e-9);
    assertEquals(result.recallAtK(), written.get("recallAtK").asDouble(), 1e-9);
    assertEquals(8, written.get("totalQueries").asInt());
    assertEquals(8, written.get("details").size());
  }
}
