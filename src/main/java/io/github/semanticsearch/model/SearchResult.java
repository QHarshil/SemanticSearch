package io.github.semanticsearch.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Data transfer object for search results. Contains document information and relevance score. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

  private UUID id;
  private String title;
  private String content;
  private Map<String, String> metadata;
  private double score;
  private List<String> highlights;
}
