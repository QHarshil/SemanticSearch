package io.github.semanticsearch.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.semanticsearch.exception.DuplicateContentException;
import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;

/**
 * Owns the document write path: content hashing, duplicate detection, persistence and search
 * indexing.
 *
 * <p>Those steps span PostgreSQL and the search index, which share no transaction. Each mutating
 * method here performs the index write inside its database transaction, so an index failure rolls
 * the row back instead of committing a document that search can never return. The cost is that a
 * search-index outage fails writes rather than silently degrading them, which is the safer default
 * for a corpus meant to be fully searchable.
 *
 * <p>The residual window is a successful index write followed by a failed commit, which leaves a
 * vector with no row behind it. Index writes are upserts keyed on the document id, so {@link
 * #reconcileUnindexed()} and {@link IndexService#rebuildIndex()} can both repair that by writing
 * the same document again; retrying never creates a duplicate vector.
 */
@Service
public class DocumentService {

  private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

  private final DocumentRepository documentRepository;
  private final IndexService indexService;
  private final LexicalIndex lexicalIndex;

  public DocumentService(
      DocumentRepository documentRepository, IndexService indexService, LexicalIndex lexicalIndex) {
    this.documentRepository = documentRepository;
    this.indexService = indexService;
    this.lexicalIndex = lexicalIndex;
  }

  /**
   * Persist and index a new document.
   *
   * <p>The content hash is always computed here and overwrites whatever the caller supplied, so a
   * client cannot claim an identity that does not match its own body.
   *
   * @throws DuplicateContentException if the content already exists
   */
  @CacheEvict(cacheNames = "searchResults", allEntries = true)
  @Transactional
  public Document create(Document submitted) {
    normalizeForInsert(submitted);
    if (documentRepository.findByContentHash(submitted.getContentHash()).isPresent()) {
      throw new DuplicateContentException("Document with same content already exists");
    }
    return persistAndIndex(submitted);
  }

  /**
   * Create the document unless its content is already stored.
   *
   * <p>Separate from {@link #create} so that callers expected to re-run over the same input -
   * seeding and fixture setup, need not drive control flow with an exception. A {@link
   * DuplicateContentException} thrown inside a transaction marks it rollback-only even if the
   * caller catches it, which would abort the rest of the batch.
   *
   * @return the created document, or empty if that content was already present
   */
  @CacheEvict(cacheNames = "searchResults", allEntries = true)
  @Transactional
  public Optional<Document> createIfAbsent(Document submitted) {
    normalizeForInsert(submitted);
    if (documentRepository.findByContentHash(submitted.getContentHash()).isPresent()) {
      return Optional.empty();
    }
    return Optional.of(persistAndIndex(submitted));
  }

  private void normalizeForInsert(Document submitted) {
    submitted.setMetadata(normalizeMetadata(submitted.getMetadata()));
    submitted.setContentHash(contentHash(submitted.getContent()));
    submitted.setVectorId(null);
    submitted.setIndexed(false);
  }

  /**
   * Writes the row, the vector and the postings.
   *
   * <p>{@link LexicalIndex} is dropped rather than updated in place, because BM25 reads corpus-wide
   * term statistics and one new document moves the document frequency of every term it holds.
   */
  private Document persistAndIndex(Document document) {
    Document indexed = indexService.indexDocument(saveDetectingDuplicate(document));
    lexicalIndex.invalidate();
    return indexed;
  }

  /**
   * Update a document's title, content and metadata, then re-index it.
   *
   * @return the updated document, or empty if no document has that id
   * @throws DuplicateContentException if the new content belongs to another document
   */
  @CacheEvict(cacheNames = "searchResults", allEntries = true)
  @Transactional
  public Optional<Document> update(UUID id, Document submitted) {
    return documentRepository
        .findById(id)
        .map(
            existing -> {
              String hash = contentHash(submitted.getContent());
              documentRepository
                  .findByContentHash(hash)
                  .filter(other -> !other.getId().equals(id))
                  .ifPresent(
                      other -> {
                        throw new DuplicateContentException(
                            "Document with same content already exists");
                      });

              existing.setTitle(submitted.getTitle());
              existing.setContent(submitted.getContent());
              existing.setMetadata(normalizeMetadata(submitted.getMetadata()));
              existing.setContentHash(hash);

              Document updated = indexService.updateDocumentIndex(saveDetectingDuplicate(existing));
              lexicalIndex.invalidate();
              return updated;
            });
  }

  /**
   * Remove a document from the search index and the database.
   *
   * <p>The vector goes first: if that fails the transaction rolls back and both systems still
   * agree. Deleting the row first would risk an orphan vector pointing at an id that no longer
   * resolves.
   *
   * @return true if a document was deleted, false if none had that id
   */
  @CacheEvict(cacheNames = "searchResults", allEntries = true)
  @Transactional
  public boolean delete(UUID id) {
    Optional<Document> found = documentRepository.findById(id);
    if (found.isEmpty()) {
      return false;
    }

    Document document = found.get();
    if (document.getVectorId() != null) {
      indexService.deleteDocumentVectors(document);
    }
    documentRepository.delete(document);
    lexicalIndex.invalidate();
    return true;
  }

  /**
   * Re-index every document the index does not have, and report how many were repaired.
   *
   * <p>A document is left with {@code indexed=false} when its row committed but the index write did
   * not, which is the state an interrupted write leaves behind. Because index writes are upserts,
   * running this when nothing is broken is a no-op beyond the wasted embeddings.
   */
  @CacheEvict(cacheNames = "searchResults", allEntries = true)
  @Transactional
  public int reconcileUnindexed() {
    List<Document> pending = documentRepository.findByIndexedFalse();
    for (Document document : pending) {
      indexService.indexDocument(document);
    }
    if (!pending.isEmpty()) {
      lexicalIndex.invalidate();
      log.info("Reconciled {} unindexed documents", pending.size());
    }
    return pending.size();
  }

  /**
   * Translates the unique-constraint violation on {@code content_hash} into the same conflict the
   * pre-flight lookup raises. The lookup alone is not enough: two concurrent writes can both pass
   * it and only fail at flush time.
   */
  private Document saveDetectingDuplicate(Document document) {
    try {
      return documentRepository.saveAndFlush(document);
    } catch (DataIntegrityViolationException e) {
      throw new DuplicateContentException("Document with same content already exists", e);
    }
  }

  private static Map<String, String> normalizeMetadata(Map<String, String> metadata) {
    return metadata == null ? new HashMap<>() : metadata;
  }

  /**
   * SHA-256 of the content, Base64 encoded, used to detect duplicates.
   *
   * <p>Encoded as UTF-8 explicitly. Relying on the platform default charset would make the hash of
   * the same text differ between machines, so identical content could be stored twice.
   */
  private static String contentHash(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return Base64.getEncoder()
          .encodeToString(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required but unavailable", e);
    }
  }
}
