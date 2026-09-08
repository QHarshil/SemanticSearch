package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.github.semanticsearch.model.Document;

class ChunkerTest {

  @Test
  void aDocumentThatFitsInOneWindowIsNotSplit() {
    // The short-corpus path. Every relevance figure measured before chunking was
    // measured on documents this size, so they have to keep producing the same
    // single passage or those numbers stop being comparable.
    Document document = document("Latency Budgets", "Latency budgets keep responses under a p95.");

    List<Chunker.Chunk> chunks = new Chunker(180, 40).chunk(document);

    assertEquals(1, chunks.size());
    assertEquals(Tokenizer.indexableText(document), chunks.get(0).text());
  }

  @Test
  void aLongerDocumentIsSplitIntoOverlappingWindows() {
    Document document = document("Notes", words(1, 25));

    List<Chunker.Chunk> chunks = new Chunker(10, 4).chunk(document);

    // The one-word title comes out of the window, leaving nine words of content
    // and a stride of five, so passages start at words 1, 6, 11, 16, 21 and the
    // last runs to the end.
    assertEquals(List.of(0, 1, 2, 3, 4), chunks.stream().map(Chunker.Chunk::ordinal).toList());
    assertTrue(chunks.get(0).text().endsWith(words(1, 9)), chunks.get(0).text());
    assertTrue(chunks.get(1).text().endsWith(words(6, 14)), chunks.get(1).text());
    assertTrue(chunks.get(4).text().endsWith(words(21, 25)), chunks.get(4).text());
  }

  @Test
  void consecutiveWindowsShareTheOverlap() {
    // A sentence that straddles a boundary has to survive whole in one passage,
    // or both halves are too weak to retrieve and the answer is unreachable.
    List<Chunker.Chunk> chunks = new Chunker(10, 4).chunk(document("Notes", words(1, 25)));

    for (int i = 1; i < chunks.size(); i++) {
      String previous = chunks.get(i - 1).text();
      String current = chunks.get(i).text();
      for (int word = 0; word < 4; word++) {
        String shared = "w" + (1 + i * 5 + word);
        assertTrue(previous.contains(shared), shared + " missing from passage " + (i - 1));
        assertTrue(current.contains(shared), shared + " missing from passage " + i);
      }
    }
  }

  @Test
  void everyWordOfTheDocumentLandsInSomePassage() {
    List<Chunker.Chunk> chunks = new Chunker(10, 4).chunk(document("Notes", words(1, 25)));
    String all = chunks.stream().map(Chunker.Chunk::text).collect(Collectors.joining(" "));

    for (int i = 1; i <= 25; i++) {
      assertTrue(all.contains("w" + i + " ") || all.endsWith("w" + i), "w" + i + " was dropped");
    }
  }

  @Test
  void everyPassageCarriesTheTitle() {
    // A passage from the middle of a long document otherwise arrives with nothing
    // saying what it belongs to.
    List<Chunker.Chunk> chunks = new Chunker(10, 4).chunk(document("Compaction", words(1, 25)));

    assertTrue(chunks.size() > 1);
    chunks.forEach(chunk -> assertTrue(chunk.text().startsWith("Compaction\n"), chunk.text()));
  }

  @Test
  void aDocumentWithNoContentStillYieldsOnePassage() {
    List<Chunker.Chunk> chunks = new Chunker(10, 4).chunk(document("Title only", ""));

    assertEquals(1, chunks.size());
    assertEquals("Title only", chunks.get(0).text());
  }

  @Test
  void noPassageExceedsTheWindowEvenWithALongTitle() {
    // The title is repeated in every passage, so an unbounded one would push each
    // passage past the size the window was chosen to fit and put text back
    // outside what the model reads.
    Document document = document(words(100, 140), words(1, 60));

    List<Chunker.Chunk> chunks = new Chunker(20, 5).chunk(document);

    assertTrue(chunks.size() > 1);
    for (Chunker.Chunk chunk : chunks) {
      int length = chunk.text().split("\\s+").length;
      assertTrue(length <= 20, "passage " + chunk.ordinal() + " holds " + length + " words");
    }
  }

  @Test
  void aDocumentWithNoTitleIsStillSplit() {
    Document document = new Document();
    document.setContent(words(1, 25));

    List<Chunker.Chunk> chunks = new Chunker(10, 4).chunk(document);

    assertTrue(chunks.size() > 1);
    assertTrue(chunks.get(0).text().startsWith("w1 "), chunks.get(0).text());
  }

  @Test
  void theBoundaryBetweenOneWindowAndTwo() {
    Chunker chunker = new Chunker(10, 4);

    assertEquals(1, chunker.chunk(document("T", words(1, 10))).size());
    assertEquals(2, chunker.chunk(document("T", words(1, 11))).size());
  }

  @Test
  void aNonBreakingSpaceSeparatesWordsLikeAnyOtherSpace() {
    // Java's \s does not match U+00A0, so text pasted from a word processor
    // would count as one enormous word and never be split.
    String content =
        String.join("\u00a0", IntStream.rangeClosed(1, 25).mapToObj(i -> "w" + i).toList());

    List<Chunker.Chunk> chunks = new Chunker(10, 4).chunk(document("T", content));

    assertTrue(chunks.size() > 1, "non-breaking spaces did not separate words");
  }

  @Test
  void rejectsAnOverlapThatWouldNeverAdvance() {
    // Overlap equal to the window means every passage starts where the last one
    // did, so the loop never advances.
    assertThrows(IllegalArgumentException.class, () -> new Chunker(10, 10));
    assertThrows(IllegalArgumentException.class, () -> new Chunker(10, 11));
    assertThrows(IllegalArgumentException.class, () -> new Chunker(0, 0));
    assertThrows(IllegalArgumentException.class, () -> new Chunker(10, -1));
  }

  private static Document document(String title, String content) {
    Document document = new Document();
    document.setTitle(title);
    document.setContent(content);
    return document;
  }

  private static String words(int from, int to) {
    return IntStream.rangeClosed(from, to).mapToObj(i -> "w" + i).collect(Collectors.joining(" "));
  }
}
