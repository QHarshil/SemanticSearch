package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;

/**
 * Retrieval and scoring from the inverted index, and the write paths that have to invalidate it.
 */
@SpringBootTest
@ActiveProfiles("test")
class LexicalIndexTest {

  @Autowired private LexicalIndex lexicalIndex;
  @Autowired private DocumentService documentService;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private IndexService indexService;

  @BeforeEach
  void resetCorpus() {
    documentRepository.deleteAll();
    indexService.rebuildIndex();
    lexicalIndex.invalidate();
  }

  @Test
  void aDeleteFollowedByACreateDoesNotLeaveTheIndexStale() {
    Document zebra = create("Zebra Report", "The quagga is an extinct subspecies of zebra.");
    create("Filler One", "Unrelated writing about storage replicas.");
    create("Filler Two", "More unrelated writing about storage replicas.");
    assertEquals(1, lexicalIndex.search("quagga", 10).size());

    // The count returns to where it started, so anything that decides freshness
    // by counting rows keeps serving postings that name the deleted document and
    // omit the new one.
    documentService.delete(zebra.getId());
    Document okapi =
        create("Okapi Report", "The okapi is the only living relative of the giraffe.");

    assertTrue(
        lexicalIndex.search("quagga", 10).isEmpty(), "the deleted document is still indexed");
    assertEquals(
        List.of(okapi.getId()),
        ids(lexicalIndex.search("okapi", 10)),
        "the new document is not retrievable");
  }

  @Test
  void aDeletedDocumentStopsBeingRetrievable() {
    Document zebra = create("Zebra Report", "The quagga is an extinct subspecies of zebra.");
    assertEquals(1, lexicalIndex.search("quagga", 10).size());

    documentService.delete(zebra.getId());

    assertTrue(lexicalIndex.search("quagga", 10).isEmpty());
  }

  @Test
  void anUpdateReplacesTheTermsTheDocumentIsFoundBy() {
    Document document = create("Notes", "The document mentions marmalade.");

    documentService.update(document.getId(), body("Notes", "The document mentions chutney."));

    assertTrue(lexicalIndex.search("marmalade", 10).isEmpty());
    assertEquals(List.of(document.getId()), ids(lexicalIndex.search("chutney", 10)));
  }

  @Test
  void ranksByBm25AndTruncatesToTheRequestedLimit() {
    for (int i = 0; i < 5; i++) {
      create("Doc " + i, "Every document here mentions retrieval. Filler number " + i + ".");
    }
    Document focused = create("Focused", "Retrieval retrieval retrieval and nothing else.");

    List<Map.Entry<UUID, Double>> top = lexicalIndex.search("retrieval", 2);

    assertEquals(2, top.size(), "the limit is not applied");
    assertEquals(focused.getId(), top.get(0).getKey(), "the densest match should rank first");
    assertTrue(top.get(0).getValue() >= top.get(1).getValue(), "results are not sorted");
  }

  @Test
  void aQueryOfNothingButStopWordsMatchesNothing() {
    create("Notes", "The document mentions marmalade.");

    // Every token is filtered before the postings are consulted, so there is no
    // term to look up. Returning the whole corpus here would put every document
    // into the candidate pool for a query that says nothing.
    assertTrue(lexicalIndex.search("what is the", 10).isEmpty());
    assertTrue(lexicalIndex.search("   ", 10).isEmpty());
  }

  @Test
  void scoreCoversOnlyTheDocumentsItIsAsked() {
    Document marmalade = create("One", "The document mentions marmalade.");
    Document also = create("Two", "This one also mentions marmalade.");

    Map<UUID, Double> restricted = lexicalIndex.score("marmalade", List.of(marmalade.getId()));

    assertEquals(Set.of(marmalade.getId()), restricted.keySet());
    assertTrue(restricted.get(marmalade.getId()) > 0.0);
    assertEquals(
        Set.of(marmalade.getId(), also.getId()),
        lexicalIndex.score("marmalade", List.of(marmalade.getId(), also.getId())).keySet());
  }

  @Test
  void scoreLeavesOutDocumentsHoldingNoQueryTerm() {
    Document marmalade = create("One", "The document mentions marmalade.");
    Document chutney = create("Two", "This one mentions chutney.");

    Map<UUID, Double> scores =
        lexicalIndex.score("marmalade", List.of(marmalade.getId(), chutney.getId()));

    // Absent, not zero. The caller distinguishes "no lexical opinion" from "a
    // lexical opinion that this is a poor match" and treats them differently.
    assertTrue(scores.containsKey(marmalade.getId()));
    assertFalse(scores.containsKey(chutney.getId()));
  }

  @Test
  void scoreOfAnEmptyCandidateSetIsEmpty() {
    create("One", "The document mentions marmalade.");

    assertTrue(lexicalIndex.score("marmalade", List.of()).isEmpty());
  }

  private Document create(String title, String content) {
    return documentService.create(body(title, content));
  }

  private static Document body(String title, String content) {
    Document document = new Document();
    document.setTitle(title);
    document.setContent(content);
    return document;
  }

  private static List<UUID> ids(List<Map.Entry<UUID, Double>> hits) {
    List<UUID> ids = new ArrayList<>();
    hits.forEach(hit -> ids.add(hit.getKey()));
    return ids;
  }
}
