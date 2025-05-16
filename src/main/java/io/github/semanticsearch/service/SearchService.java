package io.github.semanticsearch.service;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.model.SearchRequest;
import io.github.semanticsearch.model.SearchResult;
import io.github.semanticsearch.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for semantic search functionality.
 * Coordinates embedding generation, vector search, and result processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final EmbeddingService embeddingService;
    private final IndexService indexService;
    private final DocumentRepository documentRepository;

    /**
     * Perform semantic search based on query text.
     * Caches results for frequent queries to improve performance.
     *
     * @param request Search request containing query and parameters
     * @return List of search results
     */
    @Cacheable(value = "searchResults", key = "#request.toString()", unless = "#result.isEmpty()")
    public List<SearchResult> search(SearchRequest request) {
        log.debug("Performing semantic search for query: {}", request.getQuery());
        
        // Generate embedding for query
        List<Float> queryVector = embeddingService.embed(request.getQuery());
        if (queryVector.isEmpty()) {
            log.warn("Failed to generate embedding for query: {}", request.getQuery());
            return Collections.emptyList();
        }
        
        // Find similar documents
        int limit = request.getLimit() > 0 ? request.getLimit() : 10;
        float minScore = request.getMinScore() > 0 ? request.getMinScore() : 0.7f;
        List<Map.Entry<UUID, Float>> similarDocuments = 
            indexService.findSimilarDocuments(queryVector, limit, minScore);
        
        if (similarDocuments.isEmpty()) {
            log.debug("No similar documents found for query: {}", request.getQuery());
            return Collections.emptyList();
        }
        
        // Retrieve document details
        List<UUID> documentIds = similarDocuments.stream()
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        Map<UUID, Document> documentsMap = documentRepository.findAllById(documentIds).stream()
            .collect(Collectors.toMap(Document::getId, doc -> doc));
        
        // Build search results
        List<SearchResult> results = new ArrayList<>();
        for (Map.Entry<UUID, Float> entry : similarDocuments) {
            UUID documentId = entry.getKey();
            Document document = documentsMap.get(documentId);
            
            if (document != null) {
                SearchResult result = SearchResult.builder()
                    .id(document.getId())
                    .title(document.getTitle())
                    .content(request.isIncludeContent() ? document.getContent() : null)
                    .metadata(document.getMetadata())
                    .score(entry.getValue())
                    .highlights(request.isIncludeHighlights() ? 
                        generateHighlights(document.getContent(), request.getQuery()) : null)
                    .build();
                
                results.add(result);
            }
        }
        
        log.debug("Found {} results for query: {}", results.size(), request.getQuery());
        return results;
    }
    
    /**
     * Find documents similar to a given document.
     *
     * @param documentId ID of the document to find similar documents for
     * @param limit Maximum number of results to return
     * @param minScore Minimum similarity score threshold
     * @return List of search results
     */
    public List<SearchResult> findSimilarDocuments(UUID documentId, int limit, float minScore) {
        Optional<Document> documentOpt = documentRepository.findById(documentId);
        if (documentOpt.isEmpty()) {
            log.warn("Document not found: {}", documentId);
            return Collections.emptyList();
        }
        
        Document document = documentOpt.get();
        List<Float> documentVector = embeddingService.embed(document.getContent());
        if (documentVector.isEmpty()) {
            log.warn("Failed to generate embedding for document: {}", documentId);
            return Collections.emptyList();
        }
        
        // Find similar documents
        List<Map.Entry<UUID, Float>> similarDocuments = 
            indexService.findSimilarDocuments(documentVector, limit + 1, minScore);
        
        // Remove the original document from results
        similarDocuments = similarDocuments.stream()
            .filter(entry -> !entry.getKey().equals(documentId))
            .limit(limit)
            .collect(Collectors.toList());
        
        if (similarDocuments.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Retrieve document details
        List<UUID> documentIds = similarDocuments.stream()
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        Map<UUID, Document> documentsMap = documentRepository.findAllById(documentIds).stream()
            .collect(Collectors.toMap(Document::getId, doc -> doc));
        
        // Build search results
        List<SearchResult> results = new ArrayList<>();
        for (Map.Entry<UUID, Float> entry : similarDocuments) {
            Document similarDoc = documentsMap.get(entry.getKey());
            if (similarDoc != null) {
                SearchResult result = SearchResult.builder()
                    .id(similarDoc.getId())
                    .title(similarDoc.getTitle())
                    .content(similarDoc.getContent())
                    .metadata(similarDoc.getMetadata())
                    .score(entry.getValue())
                    .build();
                
                results.add(result);
            }
        }
        
        return results;
    }
    
    /**
     * Generate text highlights for search results.
     * Extracts relevant snippets from content that match the query.
     *
     * @param content Document content
     * @param query Search query
     * @return List of highlighted text snippets
     */
    private List<String> generateHighlights(String content, String query) {
        List<String> highlights = new ArrayList<>();
        if (content == null || content.isEmpty() || query == null || query.isEmpty()) {
            return highlights;
        }
        
        // Simple highlight generation by splitting content into sentences
        String[] sentences = content.split("[.!?]");
        String[] queryTerms = query.toLowerCase().split("\\s+");
        
        for (String sentence : sentences) {
            String sentenceLower = sentence.toLowerCase();
            boolean relevant = false;
            
            for (String term : queryTerms) {
                if (sentenceLower.contains(term)) {
                    relevant = true;
                    break;
                }
            }
            
            if (relevant) {
                String highlight = sentence.trim();
                if (!highlight.isEmpty()) {
                    highlights.add(highlight);
                }
                
                // Limit number of highlights
                if (highlights.size() >= 3) {
                    break;
                }
            }
        }
        
        return highlights;
    }
}
