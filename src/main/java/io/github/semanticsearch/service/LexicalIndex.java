package io.github.semanticsearch.service;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.github.semanticsearch.config.SearchProperties;
import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;
import io.github.semanticsearch.util.ScoreCalculator;

/**
 * An in-memory inverted index and the BM25 scoring built on it.
 *
 * <p>It answers two questions. {@link #search} ranks the whole corpus for a query, so a document
 * that shares the query's rare terms is retrieved even when the embedding model puts it nowhere
 * near the query. {@link #score} rates documents someone else retrieved, which is what the vector
 * side needs.
 *
 * <p>Term statistics have to be corpus-wide to mean anything. Deriving inverse document frequency
 * from the handful of documents a vector search returned would compute it over a sample of ten,
 * where a term appearing in every candidate looks rare or common essentially at random.
 *
 * <p>The postings live in memory and are rebuilt from the repository on the first query after any
 * write. That suits the corpus sizes this service targets and not much beyond them. For a large
 * corpus, push lexical retrieval into Elasticsearch, which maintains an inverted index natively and
 * can combine the two rankings itself.
 */
@Service
public class LexicalIndex {

  private static final Logger log = LoggerFactory.getLogger(LexicalIndex.class);

  private final DocumentRepository documentRepository;
  private final SearchProperties searchProperties;
  private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();

  public LexicalIndex(DocumentRepository documentRepository, SearchProperties searchProperties) {
    this.documentRepository = documentRepository;
    this.searchProperties = searchProperties;
  }

  /** Drops the index so the next query rebuilds it. Every write to the corpus has to call this. */
  public void invalidate() {
    snapshot.set(null);
  }

  /**
   * The current index, rebuilt on the first query after any invalidation.
   *
   * <p>Freshness is the writer's responsibility. Rebuilding when the row count has moved does not
   * catch every change: a delete followed by a create restores the count while the postings still
   * name the removed document and omit the new one, so the query that finds the deleted document is
   * the same query that cannot find its replacement.
   */
  public Snapshot current() {
    Snapshot existing = snapshot.get();
    if (existing != null) {
      return existing;
    }
    Snapshot rebuilt = rebuild();
    snapshot.set(rebuilt);
    return rebuilt;
  }

  /**
   * The best-matching documents for a query, most relevant first.
   *
   * <p>Only documents holding at least one query term are visited, by walking that term's postings
   * rather than the corpus.
   *
   * @return document ids with BM25 scores mapped into {@code [0,1)}, at most {@code limit} of them
   */
  public List<Map.Entry<UUID, Double>> search(String query, int limit) {
    List<Map.Entry<UUID, Double>> ranked = new ArrayList<>();
    accumulate(query, null)
        .forEach((id, score) -> ranked.add(new AbstractMap.SimpleEntry<>(id, score)));
    ranked.sort(Comparator.comparingDouble(Map.Entry<UUID, Double>::getValue).reversed());
    return ranked.size() <= limit ? List.copyOf(ranked) : List.copyOf(ranked.subList(0, limit));
  }

  /**
   * BM25 for a set of documents someone else chose.
   *
   * <p>Documents that hold no query term are left out rather than scored zero, so a caller can tell
   * "this document does not match the words" apart from "BM25 has no opinion" and decide what to do
   * about it.
   */
  public Map<UUID, Double> score(String query, Collection<UUID> documentIds) {
    if (documentIds == null || documentIds.isEmpty()) {
      return Map.of();
    }
    return accumulate(query, Set.copyOf(documentIds));
  }

  /**
   * Walks the postings of every query term and sums each document's BM25 contribution.
   *
   * @param restrictTo when non-null, only these documents are scored
   */
  private Map<UUID, Double> accumulate(String query, Set<UUID> restrictTo) {
    List<String> queryTerms = Tokenizer.tokenize(query);
    if (queryTerms.isEmpty()) {
      return Map.of();
    }

    Snapshot corpus = current();
    double k1 = searchProperties.getBm25K1();
    double b = searchProperties.getBm25B();
    Map<UUID, Double> raw = new HashMap<>();

    for (String term : new HashSet<>(queryTerms)) {
      Map<UUID, Integer> postings = corpus.postingsOf(term);
      if (postings.isEmpty()) {
        continue;
      }
      double idf = corpus.inverseDocumentFrequencyOf(term);
      for (Map.Entry<UUID, Integer> posting : postings.entrySet()) {
        UUID documentId = posting.getKey();
        if (restrictTo != null && !restrictTo.contains(documentId)) {
          continue;
        }
        double frequency = posting.getValue();
        double length = corpus.lengthOf(documentId);
        double denominator = frequency + k1 * (1 - b + b * (length / corpus.averageLength()));
        raw.merge(
            documentId,
            idf * ((frequency * (k1 + 1)) / (denominator == 0 ? 1 : denominator)),
            Double::sum);
      }
    }

    Map<UUID, Double> normalised = new HashMap<>(raw.size());
    // BM25 is unbounded above, and the rest of the pipeline blends scores in
    // [0,1] and filters on minScore in the same range. x/(x+1) is monotonic, so
    // it preserves the ranking while making the value comparable to a cosine.
    raw.forEach((id, score) -> normalised.put(id, ScoreCalculator.clamp(score / (score + 1))));
    return normalised;
  }

  private Snapshot rebuild() {
    List<Document> documents = documentRepository.findAll();
    Map<String, Map<UUID, Integer>> postings = new HashMap<>();
    Map<UUID, Integer> lengths = new HashMap<>();
    long totalTokens = 0;

    for (Document document : documents) {
      List<String> tokens = Tokenizer.tokenize(document);
      totalTokens += tokens.size();
      lengths.put(document.getId(), tokens.size());
      for (String term : tokens) {
        postings
            .computeIfAbsent(term, key -> new HashMap<>())
            .merge(document.getId(), 1, Integer::sum);
      }
    }

    int documentCount = documents.size();
    double averageLength = documentCount == 0 ? 1.0 : (double) totalTokens / documentCount;
    log.debug(
        "Rebuilt the lexical index: {} documents, {} distinct terms, average length {}",
        documentCount,
        postings.size(),
        averageLength);
    return new Snapshot(
        documentCount, Math.max(1.0, averageLength), Map.copyOf(postings), Map.copyOf(lengths));
  }

  /**
   * @param documentCount total documents in the corpus
   * @param averageLength mean token count per document, never below 1 so it is safe as a divisor
   * @param postings for each term, the documents holding it and how often
   * @param documentLengths token count per document
   */
  public record Snapshot(
      int documentCount,
      double averageLength,
      Map<String, Map<UUID, Integer>> postings,
      Map<UUID, Integer> documentLengths) {

    public Map<UUID, Integer> postingsOf(String term) {
      return postings.getOrDefault(term, Map.of());
    }

    public int documentFrequencyOf(String term) {
      return postingsOf(term).size();
    }

    public int lengthOf(UUID documentId) {
      return documentLengths.getOrDefault(documentId, 0);
    }

    /** Smoothed inverse document frequency, the BM25 form that stays positive for common terms. */
    public double inverseDocumentFrequencyOf(String term) {
      int df = Math.max(1, documentFrequencyOf(term));
      return Math.log((documentCount - df + 0.5) / (df + 0.5) + 1.0);
    }
  }
}
