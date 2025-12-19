package io.github.semanticsearch.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
@Entity
@Table(name = "documents")
@EntityListeners(AuditingEntityListener.class)
public class Document {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotBlank(message = "Title is required")
  @Column(nullable = false)
  private String title;

  @NotBlank(message = "Content is required")
  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  @Column(name = "content_hash", nullable = false, unique = true)
  private String contentHash;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "document_metadata", joinColumns = @JoinColumn(name = "document_id"))
  @MapKeyColumn(name = "metadata_key")
  @Column(name = "metadata_value")
  private Map<String, String> metadata = new HashMap<>();

  @Column(name = "vector_id")
  private String vectorId;

  @Column(name = "indexed", nullable = false)
  private boolean indexed = false;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Document() {}

  public Document(
      UUID id,
      String title,
      String content,
      String contentHash,
      Map<String, String> metadata,
      String vectorId,
      boolean indexed,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.title = title;
    this.content = content;
    this.contentHash = contentHash;
    this.metadata = metadata != null ? metadata : new HashMap<>();
    this.vectorId = vectorId;
    this.indexed = indexed;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getContentHash() {
    return contentHash;
  }

  public void setContentHash(String contentHash) {
    this.contentHash = contentHash;
  }

  public Map<String, String> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, String> metadata) {
    this.metadata = metadata != null ? metadata : new HashMap<>();
  }

  public String getVectorId() {
    return vectorId;
  }

  public void setVectorId(String vectorId) {
    this.vectorId = vectorId;
  }

  public boolean isIndexed() {
    return indexed;
  }

  public void setIndexed(boolean indexed) {
    this.indexed = indexed;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
