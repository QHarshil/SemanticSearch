package io.github.semanticsearch.controller;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;
import io.github.semanticsearch.service.IndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller for document management operations. Provides endpoints for CRUD operations on
 * documents.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Document API", description = "API for document management operations")
public class DocumentController {

  private final DocumentRepository documentRepository;
  private final IndexService indexService;

  /**
   * Create a new document. Generates content hash and indexes the document for search.
   *
   * @param document Document to create
   * @return Created document
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Create document",
      description = "Create a new document and index it for search",
      responses = {
        @ApiResponse(
            responseCode = "201",
            description = "Document created",
            content = @Content(schema = @Schema(implementation = Document.class))),
        @ApiResponse(responseCode = "400", description = "Invalid document data"),
        @ApiResponse(
            responseCode = "409",
            description = "Document with same content already exists")
      })
  public ResponseEntity<Document> createDocument(@Valid @RequestBody Document document) {
    log.debug("Creating document: {}", document.getTitle());

    // Generate content hash
    String contentHash = generateContentHash(document.getContent());
    document.setContentHash(contentHash);

    // Check if document with same content already exists
    Optional<Document> existingDocument = documentRepository.findByContentHash(contentHash);
    if (existingDocument.isPresent()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Document with same content already exists");
    }

    // Save document
    document.setIndexed(false);
    Document savedDocument = documentRepository.save(document);

    // Index document for search
    Document indexedDocument = indexService.indexDocument(savedDocument);

    return ResponseEntity.status(HttpStatus.CREATED).body(indexedDocument);
  }

  /**
   * Get document by ID.
   *
   * @param id Document ID
   * @return Document if found
   */
  @GetMapping("/{id}")
  @Operation(
      summary = "Get document",
      description = "Get document by ID",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Document found",
            content = @Content(schema = @Schema(implementation = Document.class))),
        @ApiResponse(responseCode = "404", description = "Document not found")
      })
  public ResponseEntity<Document> getDocument(
      @Parameter(description = "Document ID") @PathVariable UUID id) {
    log.debug("Getting document: {}", id);
    return documentRepository
        .findById(id)
        .map(ResponseEntity::ok)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
  }

  /**
   * Update document. Updates document content and re-indexes it for search.
   *
   * @param id Document ID
   * @param document Updated document data
   * @return Updated document
   */
  @PutMapping("/{id}")
  @Operation(
      summary = "Update document",
      description = "Update document content and re-index it for search",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Document updated",
            content = @Content(schema = @Schema(implementation = Document.class))),
        @ApiResponse(responseCode = "404", description = "Document not found"),
        @ApiResponse(responseCode = "400", description = "Invalid document data")
      })
  public ResponseEntity<Document> updateDocument(
      @Parameter(description = "Document ID") @PathVariable UUID id,
      @Valid @RequestBody Document document) {

    log.debug("Updating document: {}", id);

    return documentRepository
        .findById(id)
        .map(
            existingDocument -> {
              // Update fields
              existingDocument.setTitle(document.getTitle());
              existingDocument.setContent(document.getContent());
              existingDocument.setMetadata(document.getMetadata());

              // Generate new content hash
              String contentHash = generateContentHash(document.getContent());
              existingDocument.setContentHash(contentHash);

              // Save updated document
              Document savedDocument = documentRepository.save(existingDocument);

              // Re-index document for search
              Document indexedDocument = indexService.updateDocumentIndex(savedDocument);

              return ResponseEntity.ok(indexedDocument);
            })
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
  }

  /**
   * Delete document. Removes document from database and search index.
   *
   * @param id Document ID
   * @return No content response
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Delete document",
      description = "Delete document and remove from search index",
      responses = {
        @ApiResponse(responseCode = "204", description = "Document deleted"),
        @ApiResponse(responseCode = "404", description = "Document not found")
      })
  public ResponseEntity<Void> deleteDocument(
      @Parameter(description = "Document ID") @PathVariable UUID id) {
    log.debug("Deleting document: {}", id);

    return documentRepository
        .findById(id)
        .map(
            document -> {
              // Delete from search index
              if (document.getVectorId() != null) {
                indexService.deleteDocumentVector(document.getVectorId());
              }

              // Delete from database
              documentRepository.delete(document);

              return ResponseEntity.noContent().<Void>build();
            })
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
  }

  /**
   * List documents with pagination and sorting.
   *
   * @param page Page number
   * @param size Page size
   * @param sort Sort field
   * @param direction Sort direction
   * @return Page of documents
   */
  @GetMapping
  @Operation(
      summary = "List documents",
      description = "List documents with pagination and sorting",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Documents found",
            content = @Content(schema = @Schema(implementation = Page.class)))
      })
  public ResponseEntity<Page<Document>> listDocuments(
      @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
      @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sort,
      @Parameter(description = "Sort direction") @RequestParam(defaultValue = "DESC")
          String direction) {

    log.debug(
        "Listing documents: page={}, size={}, sort={}, direction={}", page, size, sort, direction);

    Sort.Direction sortDirection = Sort.Direction.fromString(direction);
    PageRequest pageRequest = PageRequest.of(page, size, sortDirection, sort);

    Page<Document> documents = documentRepository.findAll(pageRequest);
    return ResponseEntity.ok(documents);
  }

  /**
   * Search documents by text in title or content.
   *
   * @param text Text to search for
   * @param page Page number
   * @param size Page size
   * @return Page of documents
   */
  @GetMapping("/search")
  @Operation(
      summary = "Search documents by text",
      description = "Search documents by text in title or content (keyword search, not semantic)",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Documents found",
            content = @Content(schema = @Schema(implementation = Page.class)))
      })
  public ResponseEntity<Page<Document>> searchDocuments(
      @Parameter(description = "Search text") @RequestParam String text,
      @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {

    log.debug("Searching documents by text: {}", text);

    if (StringUtils.isBlank(text)) {
      return ResponseEntity.ok(Page.empty());
    }

    PageRequest pageRequest = PageRequest.of(page, size);
    Page<Document> documents =
        documentRepository.findByTitleOrContentContainingIgnoreCase(text, pageRequest);
    return ResponseEntity.ok(documents);
  }

  /**
   * Generate SHA-256 hash of document content. Used to detect duplicate documents.
   *
   * @param content Document content
   * @return Base64-encoded hash
   */
  private String generateContentHash(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(content.getBytes());
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      log.error("Failed to generate content hash", e);
      throw new RuntimeException("Failed to generate content hash", e);
    }
  }
}
