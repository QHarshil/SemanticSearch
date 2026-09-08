package io.github.semanticsearch.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Parsing rules for the BEIR file format, checked against a fixture small enough to read.
 *
 * <p>Worth its own test because the benchmark's numbers are only as good as this. A qrels row read
 * against the wrong query, or a zero judgement counted as relevant, produces a report that looks
 * plausible and is wrong, and nothing downstream would notice.
 */
class BeirDatasetTest {

  @TempDir Path root;

  private Path dataset;

  @BeforeEach
  void writeFixture() throws IOException {
    dataset = root.resolve("tiny");
    Files.createDirectories(dataset.resolve("qrels"));

    write(
        dataset.resolve("corpus.jsonl"),
        """
        {"_id": "d1", "title": "First", "text": "The first document.", "metadata": {}}
        {"_id": "d2", "title": "", "text": "The second document."}
        {"_id": "d3", "title": "Third", "text": "The third document."}
        """);
    write(
        dataset.resolve("queries.jsonl"),
        """
        {"_id": "q1", "text": "first query"}
        {"_id": "q2", "text": "second query"}
        {"_id": "q9", "text": "a query from the train split"}
        """);
    write(
        dataset.resolve("qrels").resolve("test.tsv"),
        """
        query-id\tcorpus-id\tscore
        q1\td1\t1
        q1\td3\t1
        q2\td2\t1
        q2\td3\t0
        """);
  }

  @Test
  void readsTheCorpusIncludingDocumentsWithNoTitle() throws IOException {
    BeirDataset loaded = BeirDataset.load(dataset, "test.tsv");

    assertEquals(3, loaded.corpus().size());
    assertEquals("First", loaded.corpus().get(0).title());
    assertEquals("The first document.", loaded.corpus().get(0).text());
    assertEquals("", loaded.corpus().get(1).title(), "an absent title must read as empty");
  }

  @Test
  void groupsEveryRelevantDocumentUnderItsQuery() throws IOException {
    BeirDataset loaded = BeirDataset.load(dataset, "test.tsv");

    assertEquals(Set.of("d1", "d3"), loaded.relevant().get("q1"));
  }

  @Test
  void treatsAZeroJudgementAsNotRelevant() throws IOException {
    // The row exists and says the document is not an answer. Counting it would
    // turn a documented miss into a hit and inflate every metric.
    BeirDataset loaded = BeirDataset.load(dataset, "test.tsv");

    assertEquals(Set.of("d2"), loaded.relevant().get("q2"));
  }

  @Test
  void evaluatesOnlyTheQueriesTheSplitJudges() throws IOException {
    // queries.jsonl carries every split. Scoring q9, which has no judgements,
    // would count a query with no possible right answer as a failure.
    BeirDataset loaded = BeirDataset.load(dataset, "test.tsv");

    assertEquals(
        List.of("q1", "q2"), loaded.queries().stream().map(BeirDataset.BeirQuery::id).toList());
  }

  @Test
  void failsWhenTheQrelsNameAQueryTheFileDoesNotDefine() throws IOException {
    write(
        dataset.resolve("qrels").resolve("test.tsv"),
        """
        query-id\tcorpus-id\tscore
        q404\td1\t1
        """);

    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> BeirDataset.load(dataset, "test.tsv"));

    assertTrue(thrown.getMessage().contains("q404"), thrown.getMessage());
  }

  @Test
  void refusesAZipEntryThatWritesOutsideTheTargetDirectory() throws IOException {
    Path archive = root.resolve("evil.zip");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
      zip.putNextEntry(new ZipEntry("../escaped.txt"));
      zip.write("owned".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }

    Path target = root.resolve("unpacked");
    assertThrows(IOException.class, () -> BeirDataset.unzip(archive, target));
    assertTrue(Files.notExists(root.resolve("escaped.txt")), "the entry escaped the target");
  }

  @Test
  void unpacksAWellFormedArchive() throws IOException {
    Path archive = root.resolve("good.zip");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
      zip.putNextEntry(new ZipEntry("tiny/corpus.jsonl"));
      zip.write("{\"_id\": \"d1\", \"text\": \"hello\"}".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }

    Path target = root.resolve("unpacked");
    BeirDataset.unzip(archive, target);

    assertTrue(Files.isRegularFile(target.resolve("tiny").resolve("corpus.jsonl")));
  }

  private static void write(Path path, String content) throws IOException {
    try (OutputStream out = Files.newOutputStream(path)) {
      out.write(content.getBytes(StandardCharsets.UTF_8));
    }
  }
}
