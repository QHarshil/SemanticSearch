package io.github.semanticsearch.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;

/**
 * Corpus-level term statistics for BM25: document count, average document length, and per-term
 * document frequency.
 *
 * <p>These have to be corpus-wide to mean anything. Deriving them from the candidate set a vector
 * search returns - at most {@code limit} documents - would compute inverse document frequency over
 * a sample of ten, where a term appearing in every candidate looks rare or common essentially at
 * random. That is BM25's formula applied to numbers too small to support it.
 *
 * <p>The statistics are held in memory and recomputed from the repository after any write. That is
 * appropriate for the corpus sizes this service targets. For a large corpus you would push lexical
 * scoring into Elasticsearch, which maintains these statistics natively as part of the inverted
 * index, and combine the two rankings there instead.
 */
@Service
public class CorpusStatistics {

  private static final Logger log = LoggerFactory.getLogger(CorpusStatistics.class);

  private final DocumentRepository documentRepository;
  private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();

  public CorpusStatistics(DocumentRepository documentRepository) {
    this.documentRepository = documentRepository;
  }

  /**
   * Drops the cached statistics. Only needed when a document's <em>content</em> changes, since
   * {@link #current()} detects additions and removals on its own.
   */
  public void invalidate() {
    snapshot.set(null);
  }

  /**
   * Returns the current statistics, recomputing them when the corpus has changed size.
   *
   * <p>Checking the document count makes the cache self-validating for inserts and deletes, so
   * callers do not have to remember to invalidate on every write path. Content edits keep the count
   * the same and do require {@link #invalidate()}.
   */
  public Snapshot current() {
    Snapshot existing = snapshot.get();
    if (existing != null && existing.documentCount() == documentRepository.count()) {
      return existing;
    }
    Snapshot rebuilt = rebuild();
    snapshot.set(rebuilt);
    return rebuilt;
  }

  private Snapshot rebuild() {
    List<Document> documents = documentRepository.findAll();
    Map<String, Integer> documentFrequency = new HashMap<>();
    long totalTokens = 0;

    for (Document document : documents) {
      List<String> tokens = Tokenizer.tokenize(document);
      totalTokens += tokens.size();
      Set<String> distinct = new HashSet<>(tokens);
      for (String term : distinct) {
        documentFrequency.merge(term, 1, Integer::sum);
      }
    }

    int documentCount = documents.size();
    double averageLength = documentCount == 0 ? 1.0 : (double) totalTokens / documentCount;
    log.debug(
        "Rebuilt corpus statistics: {} documents, {} distinct terms, average length {}",
        documentCount,
        documentFrequency.size(),
        averageLength);
    return new Snapshot(documentCount, Math.max(1.0, averageLength), Map.copyOf(documentFrequency));
  }

  /**
   * @param documentCount total documents in the corpus
   * @param averageLength mean token count per document, never below 1 so it is safe as a divisor
   * @param documentFrequency number of documents containing each term
   */
  public record Snapshot(
      int documentCount, double averageLength, Map<String, Integer> documentFrequency) {

    public int documentFrequencyOf(String term) {
      return documentFrequency.getOrDefault(term, 0);
    }
  }
}
