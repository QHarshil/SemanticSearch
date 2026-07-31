package io.github.semanticsearch.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.semanticsearch.model.Document;

/** Seeds a small set of demo documents for evaluation and smoke tests. */
@Service
public class SeedService {

  private static final Logger log = LoggerFactory.getLogger(SeedService.class);

  private final DocumentService documentService;

  public SeedService(DocumentService documentService) {
    this.documentService = documentService;
  }

  /**
   * The demo corpus, also the corpus the curated eval in {@link EvalService} measures against.
   *
   * <p>Eight documents across distinct topics, deliberately more than the {@code k} the eval
   * measures at: with a corpus smaller than k every query retrieves everything and Recall@k is
   * trivially 1.0. Content runs to a few sentences each so BM25 document-length normalisation has
   * something to work with.
   *
   * <p>Writes go through {@link DocumentService} rather than the repository so seeded documents are
   * hashed, indexed and cache-invalidated exactly as documents created over the API are.
   */
  @Transactional
  public void seedDemoDocuments() {
    List<Document> docs =
        List.of(
            doc(
                "Vector Search Basics",
                "Vector search finds similar documents by comparing embeddings rather than "
                    + "matching keywords. Each document is mapped to a point in a high dimensional "
                    + "space and nearby points are treated as related.",
                Map.of("topic", "search")),
            doc(
                "Ranking Signals",
                "Ranking blends relevance signals such as semantic similarity, lexical overlap and "
                    + "metadata boosts. Weighting these signals differently changes which results "
                    + "surface first.",
                Map.of("topic", "ranking")),
            doc(
                "Latency Budgets",
                "Latency budgets keep search responses under a target p95. Every stage of the "
                    + "pipeline, from embedding the query to fetching documents, spends part of "
                    + "that budget.",
                Map.of("topic", "performance")),
            doc(
                "Recency and Freshness",
                "Recency decay lowers the score of older documents so fresh content surfaces "
                    + "first. The half life controls how quickly an ageing document loses ground.",
                Map.of("topic", "ranking")),
            doc(
                "Evaluating Relevance",
                "Offline evaluation compares ranked results against a gold set using metrics like "
                    + "MRR, NDCG and recall. Without a gold set, tuning relevance is guesswork.",
                Map.of("topic", "evaluation")),
            doc(
                "Inverted Indexes and BM25",
                "An inverted index maps each term to the documents containing it. BM25 scores those "
                    + "matches using term frequency, document length and how rare the term is "
                    + "across the corpus.",
                Map.of("topic", "search")),
            doc(
                "Chunking Long Documents",
                "Long documents are split into smaller passages before embedding, because a single "
                    + "vector cannot represent many unrelated sections at once.",
                Map.of("topic", "indexing")),
            doc(
                "Caching Query Results",
                "Caching repeated queries avoids recomputing embeddings and re-running retrieval. "
                    + "A cache key must cover every parameter that changes the result.",
                Map.of("topic", "performance")));

    for (Document d : docs) {
      documentService
          .createIfAbsent(d)
          .ifPresentOrElse(
              saved -> log.info("Seeded {}", saved.getTitle()),
              () -> log.info("Seed document already present: {}", d.getTitle()));
    }
  }

  private Document doc(String title, String content, Map<String, String> metadata) {
    Document d = new Document();
    d.setTitle(title);
    d.setContent(content);
    d.setMetadata(metadata);
    return d;
  }
}
