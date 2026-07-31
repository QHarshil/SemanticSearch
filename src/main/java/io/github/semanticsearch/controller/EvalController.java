package io.github.semanticsearch.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.semanticsearch.repository.DocumentRepository;
import io.github.semanticsearch.service.EvalService;
import io.github.semanticsearch.service.SeedService;

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

  @GetMapping("/run")
  public ResponseEntity<EvalService.EvalResult> run() {
    seedService.seedDemoDocuments();
    EvalService.EvalResult result = evalService.runCuratedEval(5);
    return ResponseEntity.ok(result);
  }
}
