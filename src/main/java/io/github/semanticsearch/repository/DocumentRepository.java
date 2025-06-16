package io.github.semanticsearch.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.semanticsearch.model.Document;

/**
 * Repository for Document entity operations. Provides methods for CRUD operations and custom
 * queries.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

  /**
   * Find document by content hash.
   *
   * @param contentHash The hash of the document content
   * @return Optional document if found
   */
  Optional<Document> findByContentHash(String contentHash);

  /**
   * Find documents by indexed status.
   *
   * @param indexed The indexed status
   * @param pageable Pagination information
   * @return Page of documents
   */
  Page<Document> findByIndexed(boolean indexed, Pageable pageable);

  /**
   * Find documents containing the given text in title or content.
   *
   * @param text The text to search for
   * @param pageable Pagination information
   * @return Page of documents
   */
  @Query(
      "SELECT d FROM Document d WHERE LOWER(d.title) LIKE LOWER(CONCAT('%', :text, '%')) OR LOWER(d.content) LIKE LOWER(CONCAT('%', :text, '%'))")
  Page<Document> findByTitleOrContentContainingIgnoreCase(
      @Param("text") String text, Pageable pageable);

  /**
   * Find documents by metadata key and value.
   *
   * @param key The metadata key
   * @param value The metadata value
   * @return List of documents
   */
  @Query("SELECT d FROM Document d JOIN d.metadata m WHERE KEY(m) = :key AND VALUE(m) = :value")
  List<Document> findByMetadata(@Param("key") String key, @Param("value") String value);

  /**
   * Find documents by vector ID.
   *
   * @param vectorId The vector ID
   * @return Optional document if found
   */
  Optional<Document> findByVectorId(String vectorId);
}
