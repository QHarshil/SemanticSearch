package io.github.semanticsearch.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.semanticsearch.model.Document;

/**
 * Shared tokenizer for lexical scoring.
 *
 * <p>Document frequency and term frequency must come from identical tokenization or BM25 silently
 * mismatches terms, so {@link CorpusStatistics} and {@link SearchService} both go through here
 * rather than each splitting strings their own way.
 */
final class Tokenizer {

  /**
   * Splits on runs of non-alphanumeric characters. Unicode-aware, unlike a {@code [^a-z0-9]+}
   * split, which silently discards accented and non-Latin text.
   */
  private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

  /**
   * Common English function words, dropped from both embeddings and BM25.
   *
   * <p>They appear in nearly every document, so they carry almost no information about what a
   * document is about, but they still consume weight in the vector. A natural-language query like
   * "how does embedding similarity work" is majority function words; without this filter their
   * contribution swamps the two terms that actually identify the subject.
   *
   * <p>Kept deliberately short. An aggressive list starts removing words that matter in a technical
   * corpus - "can", "will" and "no" all appear in real queries.
   */
  private static final Set<String> STOP_WORDS =
      Set.of(
          "a", "an", "and", "are", "as", "at", "be", "been", "but", "by", "do", "does", "for",
          "from", "had", "has", "have", "how", "i", "if", "in", "into", "is", "it", "its", "of",
          "on", "or", "so", "such", "than", "that", "the", "their", "then", "there", "these",
          "they", "this", "to", "was", "were", "what", "when", "where", "which", "who", "why",
          "will", "with", "would", "you", "your");

  private Tokenizer() {}

  static List<String> tokenize(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(NON_WORD.split(text.toLowerCase(Locale.ROOT)))
        .filter(token -> !token.isEmpty())
        .filter(token -> !STOP_WORDS.contains(token))
        .toList();
  }

  /**
   * The text a document is indexed and scored on: title followed by content.
   *
   * <p>The title is included because it is usually the most concentrated description of what a
   * document is about. Embedding and scoring content alone would leave a query that matches a title
   * exactly unable to retrieve the document.
   */
  static String indexableText(Document document) {
    String title = document.getTitle() == null ? "" : document.getTitle();
    String content = document.getContent() == null ? "" : document.getContent();
    return (title + "\n" + content).strip();
  }

  static List<String> tokenize(Document document) {
    return tokenize(indexableText(document));
  }
}
