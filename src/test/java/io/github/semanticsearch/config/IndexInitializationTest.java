package io.github.semanticsearch.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;
import io.github.semanticsearch.service.EmbeddingService;
import io.github.semanticsearch.service.IndexService;

/**
 * What startup does about vectors that did not survive the last shutdown.
 *
 * <p>The in-process index is the default and lives in the heap. Rows outlive it, and they are all
 * marked indexed from before the restart, so {@code reconcileUnindexed} sees nothing to repair.
 * Without a rebuild at boot the service answers on BM25 alone and reports no problem.
 */
@SpringBootTest
@ActiveProfiles("test")
class IndexInitializationTest {

  @Autowired private IndexInitializationConfig initialization;
  @Autowired private IndexService indexService;
  @Autowired private EmbeddingService embeddingService;
  @Autowired private DocumentRepository documentRepository;

  @BeforeEach
  void resetCorpus() {
    documentRepository.deleteAll();
    indexService.rebuildIndex();
  }

  @Test
  void startupRefillsAnEmptyInProcessIndexFromTheStoredRows() {
    Document stored = index("Latency Budgets", "Latency budgets keep responses under a p95.");
    assertTrue(retrievable(stored));

    // What a restart leaves behind: the row still there and marked indexed, the
    // vectors gone with the heap that held them.
    dropTheVectorsWithoutTouchingTheRows();
    assertFalse(retrievable(stored), "the fixture did not actually empty the index");
    assertTrue(documentRepository.findById(stored.getId()).orElseThrow().isIndexed());
    assertEquals(0, documentRepository.findByIndexedFalse().size(), "nothing looks broken");

    initialization.initializeIndexOnStartup();

    assertTrue(retrievable(stored), "the index was not rebuilt at startup");
  }

  @Test
  void startupOverAnEmptyCorpusDoesNothing() {
    initialization.initializeIndexOnStartup();

    assertEquals(0, documentRepository.count());
  }

  /** Empties the vector index and leaves every row as it is, which is what a restart does. */
  private void dropTheVectorsWithoutTouchingTheRows() {
    documentRepository.findAll().forEach(indexService::deleteDocumentVectors);
  }

  private boolean retrievable(Document document) {
    return indexService
        .findSimilarDocuments(embeddingService.embed("latency budget p95"), 5, 0.0)
        .stream()
        .anyMatch(hit -> hit.getKey().equals(document.getId()));
  }

  private Document index(String title, String content) {
    Document document = new Document();
    document.setTitle(title);
    document.setContent(content);
    document.setContentHash(UUID.randomUUID().toString());
    document.setMetadata(Map.of("topic", "performance"));
    return indexService.indexDocument(documentRepository.save(document));
  }
}
