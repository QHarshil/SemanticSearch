package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import io.github.semanticsearch.repository.DocumentRepository;

/**
 * The same gold set {@link EvalServiceIntegrationTest} runs, scored with the semantic model.
 *
 * <p>Running both is the point. The pair is the measurement behind the claim that a semantic model
 * is worth its download, and it keeps that claim tied to something the build checks rather than to
 * a number written down once.
 */
@SpringBootTest(properties = "embedding.provider=onnx")
@ActiveProfiles("test")
class OnnxEvalTest {

  // Measured with all-MiniLM-L6-v2: MRR 0.854, NDCG@5 0.891, Recall@5 1.000,
  // against 0.635 / 0.695 / 0.875 for the hashing embedder on the same corpus and
  // the same eight queries. Six of the eight rank their gold document first,
  // against four.
  //
  // Thresholds sit below the measured values so ordinary tuning does not break the
  // build, and above everything the lexical embedder reaches, so a configuration
  // change that quietly falls back to it fails here.
  private static final double MIN_MRR = 0.80;

  private static final double MIN_NDCG = 0.85;
  private static final double MIN_RECALL = 0.95;

  @Autowired private SeedService seedService;
  @Autowired private EvalService evalService;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private IndexService indexService;
  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void seedCorpus() {
    documentRepository.deleteAll();
    indexService.rebuildIndex();
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    seedService.seedDemoDocuments();
  }

  @Test
  void theSemanticModelBeatsTheLexicalOneOnTheGoldSet() {
    EvalService.EvalResult result = evalService.runCuratedEval(5);

    assertEquals(8, result.totalQueries());
    assertTrue(result.mrr() >= MIN_MRR, "MRR " + result.mrr());
    assertTrue(result.ndcg() >= MIN_NDCG, "NDCG@5 " + result.ndcg());
    assertTrue(result.recallAtK() >= MIN_RECALL, "Recall@5 " + result.recallAtK());
  }

  @Test
  void retrievesTheQueryThatSharesNoWordsWithItsDocument() {
    // "what affects result ordering" and "Ranking Signals" have no content word
    // in common, so the lexical embedder never retrieves it at all and scores
    // 0.0 on every metric. Asserted on its own rather than left to the averages
    // above, because it is the one query that only meaning can answer.
    //
    // Measured: rank 2, so reciprocal rank 0.5. Second place, not first, is what
    // this model does here.
    EvalService.QueryEval ordering = queryEval("what affects result ordering");

    assertTrue(ordering.rr() >= 0.5, "reciprocal rank " + ordering.rr());
    assertEquals(1.0, ordering.recall(), "recall " + ordering.recall());
  }

  private EvalService.QueryEval queryEval(String query) {
    return evalService.runCuratedEval(5).details().stream()
        .filter(q -> q.query().equals(query))
        .findFirst()
        .orElseThrow();
  }
}
