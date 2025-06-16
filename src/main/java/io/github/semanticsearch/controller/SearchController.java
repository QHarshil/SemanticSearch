package io.github.semanticsearch.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.github.semanticsearch.model.SearchRequest;
import io.github.semanticsearch.model.SearchResult;
import io.github.semanticsearch.service.IndexService;
import io.github.semanticsearch.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller for search operations. Provides endpoints for semantic search and similar document
 * search.
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Search API", description = "API for semantic search operations")
public class SearchController {

  private final SearchService searchService;
  private final IndexService indexService;

  /**
   * Perform semantic search based on query text.
   *
   * @param query Search query text
   * @param limit Maximum number of results to return
   * @param minScore Minimum similarity score threshold
   * @param includeContent Whether to include document content in results
   * @return List of search results
   */
  @GetMapping
  @Operation(
      summary = "Semantic search",
      description = "Search documents semantically based on query text",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Search results",
            content = @Content(schema = @Schema(implementation = SearchResult.class)))
      })
  public ResponseEntity<List<SearchResult>> search(
      @Parameter(description = "Search query text") @RequestParam String query,
      @Parameter(description = "Maximum number of results")
          @RequestParam(defaultValue = "10")
          @Min(1)
          int limit,
      @Parameter(description = "Minimum similarity score") @RequestParam(defaultValue = "0.7")
          float minScore,
      @Parameter(description = "Include document content") @RequestParam(defaultValue = "true")
          boolean includeContent,
      @Parameter(description = "Include text highlights") @RequestParam(defaultValue = "true")
          boolean includeHighlights) {

    log.debug("Search request: query={}, limit={}, minScore={}", query, limit, minScore);

    SearchRequest request =
        SearchRequest.builder()
            .query(query)
            .limit(limit)
            .minScore(minScore)
            .includeContent(includeContent)
            .includeHighlights(includeHighlights)
            .build();

    List<SearchResult> results = searchService.search(request);
    return ResponseEntity.ok(results);
  }

  /**
   * Perform advanced search with additional parameters.
   *
   * @param request Search request with advanced parameters
   * @return List of search results
   */
  @PostMapping("/advanced")
  @Operation(
      summary = "Advanced semantic search",
      description = "Search documents with advanced parameters and filters",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Search results",
            content = @Content(schema = @Schema(implementation = SearchResult.class)))
      })
  public ResponseEntity<List<SearchResult>> advancedSearch(
      @Valid @RequestBody SearchRequest request) {
    log.debug("Advanced search request: {}", request);
    List<SearchResult> results = searchService.search(request);
    return ResponseEntity.ok(results);
  }

  /**
   * Find documents similar to a given document.
   *
   * @param id Document ID to find similar documents for
   * @param limit Maximum number of results to return
   * @param minScore Minimum similarity score threshold
   * @return List of search results
   */
  @GetMapping("/similar/{id}")
  @Operation(
      summary = "Find similar documents",
      description = "Find documents similar to a given document",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Similar documents",
            content = @Content(schema = @Schema(implementation = SearchResult.class))),
        @ApiResponse(responseCode = "404", description = "Document not found")
      })
  public ResponseEntity<List<SearchResult>> findSimilar(
      @Parameter(description = "Document ID") @PathVariable UUID id,
      @Parameter(description = "Maximum number of results")
          @RequestParam(defaultValue = "10")
          @Min(1)
          int limit,
      @Parameter(description = "Minimum similarity score") @RequestParam(defaultValue = "0.7")
          float minScore) {

    log.debug("Similar documents request: id={}, limit={}, minScore={}", id, limit, minScore);
    List<SearchResult> results = searchService.findSimilarDocuments(id, limit, minScore);
    return ResponseEntity.ok(results);
  }

  /**
   * Initialize or rebuild the search index. Administrative operation that requires authentication.
   *
   * @return Status message
   */
  @PostMapping("/index/rebuild")
  @Operation(
      summary = "Rebuild search index",
      description = "Initialize or rebuild the search index (admin operation)",
      responses = {@ApiResponse(responseCode = "200", description = "Index rebuilt successfully")})
  public ResponseEntity<String> rebuildIndex() {
    log.info("Rebuilding search index");
    indexService.initializeIndex();
    return ResponseEntity.ok("Index rebuilt successfully");
  }
}
