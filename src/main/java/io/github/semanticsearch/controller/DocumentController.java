package io.github.semanticsearch.controller;

import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import io.github.semanticsearch.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

/**
 * Writes are delegated to {@link DocumentService}, which owns hashing, indexing and cache
 * invalidation as one unit. Reads that need no orchestration go straight to the repository.
 */
@RestController
@RequestMapping("/api/v1/documents")
@Validated
@Tag(name = "Document API", description = "API for document management operations")
public class DocumentController {

  private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

  private final DocumentRepository documentRepository;
  private final DocumentService documentService;

  public DocumentController(
      DocumentRepository documentRepository, DocumentService documentService) {
    this.documentRepository = documentRepository;
    this.documentService = documentService;
  }

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
    return ResponseEntity.status(HttpStatus.CREATED).body(documentService.create(document));
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
        @ApiResponse(responseCode = "400", description = "Invalid document data"),
        @ApiResponse(
            responseCode = "409",
            description = "Another document already has this content")
      })
  public ResponseEntity<Document> updateDocument(
      @Parameter(description = "Document ID") @PathVariable UUID id,
      @Valid @RequestBody Document document) {

    log.debug("Updating document: {}", id);

    return documentService
        .update(id, document)
        .map(ResponseEntity::ok)
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

    if (!documentService.delete(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
    }
    return ResponseEntity.noContent().build();
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

    // fromString throws IllegalArgumentException, which reaches the catch-all
    // handler as a 500. An unrecognised direction is a client error.
    Sort.Direction sortDirection;
    try {
      sortDirection = Sort.Direction.fromString(direction);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Sort direction must be ASC or DESC, got '" + direction + "'");
    }
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
}
