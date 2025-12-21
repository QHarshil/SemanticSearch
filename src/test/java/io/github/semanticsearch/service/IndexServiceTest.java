package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractMap;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
class IndexServiceTest {

  @Autowired private IndexService indexService;

  @Autowired private EmbeddingService embeddingService;

  @Autowired private DocumentRepository documentRepository;

  @Test
  void indexesAndFindsSimilarDocumentsInStubMode() {
    Document document = new Document();
    document.setTitle("Doc One");
    document.setContent("semantic vector search document");
    document.setContentHash(hash(document.getContent()));

    Document saved = documentRepository.save(document);
    Document indexed = indexService.indexDocument(saved);

    assertTrue(indexed.isIndexed());
    assertNotNull(indexed.getVectorId());

    List<Map.Entry<UUID, Double>> similar =
        indexService.findSimilarDocuments(
            embeddingService.embed(document.getContent()), 5, 0.1);

    assertFalse(similar.isEmpty());
    assertEquals(indexed.getId(), similar.get(0).getKey());
    assertTrue(similar.get(0).getValue() > 0.0);
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
