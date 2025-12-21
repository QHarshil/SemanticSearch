package io.github.semanticsearch.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.semanticsearch.service.SeedService;

@RestController
@RequestMapping("/api/v1/documents")
public class SeedController {

  private final SeedService seedService;

  public SeedController(SeedService seedService) {
    this.seedService = seedService;
  }

  @PostMapping("/seed")
  public ResponseEntity<String> seed() {
    seedService.seedDemoDocuments();
    return ResponseEntity.ok("Seeded demo documents");
  }
}
