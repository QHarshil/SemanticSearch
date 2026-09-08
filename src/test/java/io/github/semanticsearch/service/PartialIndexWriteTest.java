package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;

/**
 * What the index holds when a document's passages cannot all be embedded.
 *
 * <p>A multi-passage write that stops partway is the case worth pinning. Writing passages as they
 * are embedded would leave the first few holding the new text and the rest holding the old, under
 * one document id, with {@code passageCount} describing neither. Every later deletion is bounded by
 * that count, so it has to describe a state the index was actually in.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PartialIndexWriteTest.FailingEmbedderConfiguration.class)
class PartialIndexWriteTest {

  private static final String ORIGINAL_MARKER = "quagga";
  private static final String REPLACEMENT_MARKER = "okapi";

  @TestConfiguration
  static class FailingEmbedderConfiguration {
    @Bean
    @Primary
    FailingEmbeddingService failingEmbeddingService(TextEmbedder embedder) {
      return new FailingEmbeddingService(embedder);
    }
  }

  /** An embedder that starts returning nothing after a set number of calls. */
  static class FailingEmbeddingService extends EmbeddingService {
    private int failAfter = Integer.MAX_VALUE;
    private int calls;

    FailingEmbeddingService(TextEmbedder embedder) {
      super(embedder, null, "hashing", 128, "unused");
    }

    void failAfter(int calls) {
      this.failAfter = calls;
      this.calls = 0;
    }

    void neverFail() {
      this.failAfter = Integer.MAX_VALUE;
    }

    @Override
    public List<Double> embed(String text) {
      return ++calls > failAfter ? List.of() : super.embed(text);
    }
  }

  @Autowired private FailingEmbeddingService embeddingService;
  @Autowired private IndexService indexService;
  @Autowired private DocumentRepository documentRepository;

  private Document document;

  @BeforeEach
  void indexALongDocument() {
    embeddingService.neverFail();
    documentRepository.deleteAll();
    indexService.rebuildIndex();

    Document written = new Document();
    written.setTitle("Field Notes");
    written.setContent(longTextAbout(ORIGINAL_MARKER));
    written.setContentHash(UUID.randomUUID().toString());
    document = indexService.indexDocument(documentRepository.save(written));
    assertTrue(document.getPassageCount() > 2, "the fixture has to split into several passages");
  }

  @AfterEach
  void stopFailing() {
    embeddingService.neverFail();
  }

  @Test
  void aWriteThatFailsPartwayLeavesTheIndexHoldingTheOldTextOnly() {
    int before = document.getPassageCount();
    document.setContent(longTextAbout(REPLACEMENT_MARKER));
    embeddingService.failAfter(1);

    Document result = indexService.indexDocument(document);
    embeddingService.neverFail();

    // The row is untouched, so passageCount still describes what is in the index.
    Document reloaded = documentRepository.findById(document.getId()).orElseThrow();
    assertEquals(before, reloaded.getPassageCount());
    assertEquals(before, result.getPassageCount());

    // And the index holds the old text throughout, with none of the new.
    assertTrue(retrieves(ORIGINAL_MARKER), "the old passages should still be there in full");
    assertFalse(
        retrieves(REPLACEMENT_MARKER), "a passage of the abandoned write reached the index");
  }

  @Test
  void aDocumentThatCannotBeEmbeddedAtAllIsNotMarkedIndexed() {
    Document fresh = new Document();
    fresh.setTitle("Unembeddable");
    fresh.setContent(longTextAbout("aardvark"));
    fresh.setContentHash(UUID.randomUUID().toString());
    Document saved = documentRepository.save(fresh);
    embeddingService.failAfter(0);

    Document result = indexService.indexDocument(saved);
    embeddingService.neverFail();

    // reconcileUnindexed selects on indexed=false, so this is what makes the
    // failure repairable instead of silent.
    assertFalse(result.isIndexed());
    assertEquals(0, result.getPassageCount());
    assertFalse(retrieves("aardvark"));
  }

  @Test
  void aRetryAfterTheProviderRecoversWritesTheWholeDocument() {
    document.setContent(longTextAbout(REPLACEMENT_MARKER));
    embeddingService.failAfter(1);
    indexService.indexDocument(document);

    embeddingService.neverFail();
    Document retried = indexService.indexDocument(document);

    assertTrue(retried.getPassageCount() > 2);
    assertTrue(retrieves(REPLACEMENT_MARKER));
    assertFalse(retrieves(ORIGINAL_MARKER), "the previous version left a tail behind");
  }

  /**
   * Whether any passage of the document under test matches a query built from {@code marker}.
   *
   * <p>Reads the index directly. The row is what a search would hydrate from, and here the row and
   * the index are exactly what might disagree.
   */
  private boolean retrieves(String marker) {
    List<Double> query = embeddingService.embed(marker + " " + marker + " " + marker);
    assertFalse(query.isEmpty(), "the probe itself must not be affected by the induced failure");
    return indexService.findSimilarDocuments(query, 5, 0.3).stream()
        .anyMatch(hit -> hit.getKey().equals(document.getId()));
  }

  /** Long enough to split, with {@code marker} repeated so any passage of it is retrievable. */
  private static String longTextAbout(String marker) {
    StringBuilder text = new StringBuilder();
    for (int paragraph = 0; paragraph < 20; paragraph++) {
      text.append("The ")
          .append(marker)
          .append(" is the subject of section ")
          .append(paragraph)
          .append(", which repeats the word ")
          .append(marker)
          .append(" so that every passage of this document mentions it plainly. ");
    }
    return text.toString();
  }
}
