package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.model.SearchRequest;
import io.github.semanticsearch.model.SearchResult;
import io.github.semanticsearch.repository.DocumentRepository;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
class SearchServiceTest {

  @Autowired private SearchService searchService;

  @Autowired private IndexService indexService;

  @Autowired private DocumentRepository documentRepository;

  @Test
  void searchReturnsHighlightsAndMetadata() {
    Document document = new Document();
    document.setTitle("Semantic Intro");
    document.setContent("semantic search highlights vector demo");
    document.setMetadata(Map.of("domain", "search"));
    document.setContentHash(hash(document.getContent()));

    Document saved = documentRepository.save(document);
    indexService.indexDocument(saved);

    SearchRequest request =
        SearchRequest.builder()
            .query(document.getContent())
            .limit(3)
            .minScore(0.1)
            .fields(List.of("domain"))
            .includeHighlights(true)
            .build();

    List<SearchResult> results = searchService.search(request);

    assertFalse(results.isEmpty());
    SearchResult first = results.get(0);
    assertEquals(saved.getId(), first.getId());
    assertEquals("search", first.getMetadata().get("domain"));
    assertNotNull(first.getHighlights());
    assertFalse(first.getHighlights().isEmpty());
  }

  private String hash(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(content.getBytes());
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unable to hash content", e);
    }
  }
}
