package io.github.semanticsearch.benchmark;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A BEIR retrieval dataset: a corpus, a set of queries and the relevance judgements between them.
 *
 * <p>BEIR ships each dataset as three files. {@code corpus.jsonl} and {@code queries.jsonl} hold
 * one JSON object per line, and {@code qrels/test.tsv} is a tab-separated table of query id, corpus
 * id and relevance. Only queries that appear in the qrels are evaluated; the queries file also
 * carries the train and dev splits, and scoring against those would report misses for judgements
 * the file does not contain.
 *
 * @param name the dataset directory name, used to label the report
 * @param corpus documents in file order
 * @param queries the test queries, in the order the qrels first mention them
 * @param relevant for each query id, the corpus ids judged relevant to it
 */
public record BeirDataset(
    String name,
    List<BeirDocument> corpus,
    List<BeirQuery> queries,
    Map<String, Set<String>> relevant) {

  /** One corpus document. BEIR gives every document a title, sometimes empty. */
  public record BeirDocument(String id, String title, String text) {}

  /** One query. */
  public record BeirQuery(String id, String text) {}

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Load from a directory holding {@code corpus.jsonl}, {@code queries.jsonl} and {@code qrels/}.
   */
  public static BeirDataset load(Path directory, String qrelsSplit) throws IOException {
    Map<String, Set<String>> relevant = readQrels(directory.resolve("qrels").resolve(qrelsSplit));
    Map<String, String> queryText = readQueries(directory.resolve("queries.jsonl"));

    List<BeirQuery> queries = new ArrayList<>();
    for (String queryId : relevant.keySet()) {
      String text = queryText.get(queryId);
      if (text == null) {
        throw new IllegalStateException(
            "qrels reference query " + queryId + ", which queries.jsonl does not define");
      }
      queries.add(new BeirQuery(queryId, text));
    }

    return new BeirDataset(
        directory.getFileName().toString(),
        readCorpus(directory.resolve("corpus.jsonl")),
        List.copyOf(queries),
        Map.copyOf(relevant));
  }

  private static List<BeirDocument> readCorpus(Path path) throws IOException {
    List<BeirDocument> corpus = new ArrayList<>();
    for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
      if (line.isBlank()) {
        continue;
      }
      JsonNode node = MAPPER.readTree(line);
      corpus.add(
          new BeirDocument(
              node.get("_id").asText(),
              node.path("title").asText(""),
              node.path("text").asText("")));
    }
    return List.copyOf(corpus);
  }

  private static Map<String, String> readQueries(Path path) throws IOException {
    Map<String, String> queries = new LinkedHashMap<>();
    for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
      if (line.isBlank()) {
        continue;
      }
      JsonNode node = MAPPER.readTree(line);
      queries.put(node.get("_id").asText(), node.path("text").asText(""));
    }
    return queries;
  }

  /**
   * Reads the qrels table, keeping only judgements with a positive relevance.
   *
   * <p>A row scored zero is an explicit statement that the document is not relevant, which is not
   * the same as its absence. Treating one as a relevant document would count a known miss as a hit.
   */
  private static Map<String, Set<String>> readQrels(Path path) throws IOException {
    Map<String, Set<String>> relevant = new LinkedHashMap<>();
    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
    for (String line : lines.subList(1, lines.size())) {
      if (line.isBlank()) {
        continue;
      }
      String[] columns = line.split("\t");
      if (columns.length < 3) {
        throw new IllegalStateException("Malformed qrels row: " + line);
      }
      if (Integer.parseInt(columns[2].trim()) > 0) {
        relevant.computeIfAbsent(columns[0], key -> new LinkedHashSet<>()).add(columns[1]);
      }
    }
    return relevant;
  }

  /**
   * Unpacks a BEIR zip into {@code target}, returning the directory holding the dataset files.
   *
   * <p>Entry names are checked against the destination before anything is written. A zip is free to
   * name an entry {@code ../../.ssh/authorized_keys}, and resolving that against the target
   * directory writes wherever the archive asks.
   */
  public static Path unzip(Path archive, Path target) throws IOException {
    Path root = target.toAbsolutePath().normalize();
    Files.createDirectories(root);
    try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
      for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        Path destination = root.resolve(entry.getName()).normalize();
        if (!destination.startsWith(root)) {
          throw new IOException("Zip entry escapes the target directory: " + entry.getName());
        }
        if (entry.isDirectory()) {
          Files.createDirectories(destination);
          continue;
        }
        Files.createDirectories(destination.getParent());
        copy(zip, destination);
      }
    }
    return root;
  }

  private static void copy(InputStream source, Path destination) throws IOException {
    try (var out = Files.newOutputStream(destination)) {
      source.transferTo(out);
    }
  }
}
