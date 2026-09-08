package io.github.semanticsearch.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.semanticsearch.repository.DocumentRepository;
import io.github.semanticsearch.service.EvalService;
import io.github.semanticsearch.service.SeedService;

import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/v1/eval")
public class EvalController {

  private final EvalService evalService;
  private final SeedService seedService;
  private final DocumentRepository documentRepository;

  public EvalController(
      EvalService evalService, SeedService seedService, DocumentRepository documentRepository) {
    this.evalService = evalService;
    this.seedService = seedService;
    this.documentRepository = documentRepository;
  }

  /**
   * Scores the curated gold set and returns MRR, NDCG@k and Recall@k.
   *
   * @param k rank cutoff for NDCG and Recall, and the number of results each query retrieves
   */
  @GetMapping("/run")
  public ResponseEntity<EvalService.EvalResult> run(
      @RequestParam(defaultValue = "5") @Positive(message = "k must be positive") int k) {
    seedService.seedDemoDocuments();
    return ResponseEntity.ok(evalService.runCuratedEval(k));
  }
}
