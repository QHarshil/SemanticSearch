package io.github.semanticsearch.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Data transfer object for search requests.
 * Contains query text and optional search parameters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {

    private String query;
    private int limit;
    private float minScore;
    private Map<String, String> filters;
    private List<String> fields;
    
    @Builder.Default
    private boolean includeContent = true;
    
    @Builder.Default
    private boolean includeHighlights = true;
}
