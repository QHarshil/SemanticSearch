package io.github.semanticsearch.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import io.github.semanticsearch.model.Document;

/**
 * Splits a document into overlapping passages, each embedded and indexed on its own.
 *
 * <p>A transformer reads a fixed number of tokens and drops the rest, so a single vector for a long
 * document is a vector for its opening. Everything past the window is unreachable by meaning, which
 * is how a document can hold the exact answer and still never be retrieved. Averaging further hurts
 * even inside the window: a document covering three subjects lands between all three and close to
 * none of them.
 *
 * <p>Windows overlap so a passage boundary cannot fall through the middle of the one sentence that
 * answers the query and leave each half too weak to retrieve.
 *
 * <p>The title is repeated at the head of every passage. It is usually the most concentrated
 * statement of what a document is about, and a passage from the middle of a long document otherwise
 * arrives with no indication of what it belongs to.
 */
public final class Chunker {

  /** Java's {@code \\s} is ASCII-only, so a non-breaking space would join two words into one. */
  private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Z}]+");

  /**
   * Most of a window that the title may take. The title is repeated in every passage, so a long one
   * would otherwise push each passage past the window it was sized to fit and reintroduce the
   * truncation passages exist to avoid.
   */
  private static final double TITLE_SHARE = 0.25;

  private final int maxWords;
  private final int overlapWords;

  /**
   * @param maxWords words per passage, counting the title repeated at its head
   * @param overlapWords words each passage repeats from the end of the one before
   */
  public Chunker(int maxWords, int overlapWords) {
    if (maxWords < 1) {
      throw new IllegalArgumentException("Chunk size must be at least 1 word, got " + maxWords);
    }
    if (overlapWords < 0 || overlapWords >= maxWords) {
      throw new IllegalArgumentException(
          "Overlap must be at least 0 and smaller than the chunk size of "
              + maxWords
              + ", got "
              + overlapWords);
    }
    this.maxWords = maxWords;
    this.overlapWords = overlapWords;
  }

  /**
   * One passage of a document.
   *
   * @param ordinal position in the document, from zero, and part of the passage's index id
   * @param text what gets embedded
   */
  public record Chunk(int ordinal, String text) {}

  /**
   * Split a document into passages, in order.
   *
   * <p>A document that fits in one window yields exactly one passage holding the same text {@link
   * Tokenizer#indexableText} produces, so short corpora index and score exactly as they would
   * without chunking.
   */
  public List<Chunk> chunk(Document document) {
    String content = document.getContent() == null ? "" : document.getContent().strip();
    String[] words = content.isEmpty() ? new String[0] : WHITESPACE.split(content);
    if (words.length <= maxWords) {
      return List.of(new Chunk(0, Tokenizer.indexableText(document)));
    }

    String title = trimmedTitle(document);
    int titleWords = title.isEmpty() ? 0 : WHITESPACE.split(title).length;
    int window = Math.max(1, maxWords - titleWords);
    int stride = Math.max(1, window - Math.min(overlapWords, window - 1));

    List<Chunk> chunks = new ArrayList<>();
    for (int start = 0; start < words.length; start += stride) {
      int end = Math.min(start + window, words.length);
      chunks.add(new Chunk(chunks.size(), head(title, words, start, end)));
      if (end == words.length) {
        break;
      }
    }
    return List.copyOf(chunks);
  }

  /** The title, cut to {@link #TITLE_SHARE} of the window so it cannot crowd out the content. */
  private String trimmedTitle(Document document) {
    String title = document.getTitle() == null ? "" : document.getTitle().strip();
    if (title.isEmpty()) {
      return "";
    }
    String[] words = WHITESPACE.split(title);
    int allowed = Math.max(1, (int) (maxWords * TITLE_SHARE));
    if (words.length <= allowed) {
      return title;
    }
    return String.join(" ", java.util.Arrays.copyOfRange(words, 0, allowed));
  }

  private static String head(String title, String[] words, int start, int end) {
    StringBuilder text = new StringBuilder();
    if (!title.isEmpty()) {
      text.append(title).append('\n');
    }
    for (int i = start; i < end; i++) {
      if (i > start) {
        text.append(' ');
      }
      text.append(words[i]);
    }
    return text.toString();
  }
}
