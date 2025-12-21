package io.github.semanticsearch.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;

/** Seeds a small set of demo documents for evaluation and smoke tests. */
@Service
public class SeedService {

  private static final Logger log = LoggerFactory.getLogger(SeedService.class);

  private final DocumentRepository documentRepository;
  private final IndexService indexService;

  public SeedService(DocumentRepository documentRepository, IndexService indexService) {
    this.documentRepository = documentRepository;
    this.indexService = indexService;
  }

  @Transactional
  public void seedDemoDocuments() {
    List<Document> docs =
        List.of(
            doc(
                "Vector Search Basics",
                "Vector search finds similar documents by comparing embeddings.",
                Map.of("topic", "search")),
            doc(
                "Ranking Signals",
                "Ranking blends relevance signals like semantic similarity and metadata boosts.",
                Map.of("topic", "ranking")),
            doc(
                "Latency Budgets",
                "Latency budgets keep search responses under target p95 milliseconds.",
                Map.of("topic", "performance")));

    for (Document d : docs) {
      documentRepository
          .findByContentHash(d.getContentHash())
          .ifPresentOrElse(
              existing -> log.info("Seed document already present: {}", existing.getTitle()),
              () -> {
                Document saved = documentRepository.save(d);
                indexService.indexDocument(saved);
                log.info("Seeded {}", saved.getTitle());
              });
    }
  }

  private Document doc(String title, String content, Map<String, String> metadata) {
    Document d = new Document();
    d.setTitle(title);
    d.setContent(content);
    d.setMetadata(metadata);
    d.setContentHash(hash(content));
    return d;
  }

  private String hash(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(content.getBytes());
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unable to hash content", e);
    }
  }
}
