package io.github.semanticsearch.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.service.IndexService;
import io.github.semanticsearch.support.InMemoryDocumentRepository;

class DocumentControllerTest {

  private InMemoryDocumentRepository repository;
  private RecordingIndexService indexService;
  private DocumentController controller;

  @BeforeEach
  void setUp() {
    repository = new InMemoryDocumentRepository();
    indexService = new RecordingIndexService();
    controller = new DocumentController(repository, indexService);
  }

  @Test
  void createDocument_setsHashAndIndexes() {
    Document input = new Document();
    input.setTitle("New Document");
    input.setContent("This is new content");
    input.setMetadata(Map.of("key", "value"));

    ResponseEntity<Document> response = controller.createDocument(input);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    Document saved = response.getBody();
    assertNotNull(saved);
    assertTrue(saved.isIndexed());
    assertNotNull(saved.getVectorId());
    assertEquals(1, repository.count());
    assertEquals(saved.getId(), indexService.lastIndexedId);
  }

  @Test
  void createDocument_rejectsDuplicateContent() {
    Document first = new Document();
    first.setTitle("Title");
    first.setContent("Duplicate body");
    controller.createDocument(first);

    Document duplicate = new Document();
    duplicate.setTitle("Duplicate title");
    duplicate.setContent("Duplicate body");

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> controller.createDocument(duplicate));

    assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    assertEquals(1, repository.count());
  }

  @Test
  void getDocument_returnsDocument() {
    Document created = controller.createDocument(makeDocument("Doc 1", "body")).getBody();
    assertNotNull(created);

    ResponseEntity<Document> response = controller.getDocument(created.getId());

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(created.getId(), response.getBody().getId());
  }

  @Test
  void getDocument_notFound() {
    UUID missing = UUID.randomUUID();
    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> controller.getDocument(missing));
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  void updateDocument_reindexesAndPersistsChanges() {
    Document created = controller.createDocument(makeDocument("Doc 1", "body")).getBody();
    assertNotNull(created);

    Document update = new Document();
    update.setTitle("Updated");
    update.setContent("Updated content");
    update.setMetadata(Map.of("author", "alice"));

    ResponseEntity<Document> response = controller.updateDocument(created.getId(), update);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    Document updated = response.getBody();
    assertNotNull(updated);
    assertEquals("Updated", updated.getTitle());
    assertEquals("Updated content", updated.getContent());
    assertEquals("alice", updated.getMetadata().get("author"));
    assertEquals(updated.getId(), indexService.lastUpdatedId);
  }

  @Test
  void deleteDocument_removesFromRepositoryAndIndex() {
    Document created = controller.createDocument(makeDocument("Doc 1", "body")).getBody();
    assertNotNull(created);

    ResponseEntity<Void> response = controller.deleteDocument(created.getId());

    assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    assertEquals(0, repository.count());
    assertEquals(created.getVectorId(), indexService.deletedVectorId);
  }

  @Test
  void listDocuments_returnsPage() {
    controller.createDocument(makeDocument("Doc 1", "body"));
    controller.createDocument(makeDocument("Doc 2", "body two"));

    ResponseEntity<Page<Document>> response = controller.listDocuments(0, 1, "createdAt", "DESC");

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(2, response.getBody().getTotalElements());
    assertEquals(1, response.getBody().getContent().size());
  }

  @Test
  void searchDocuments_returnsEmptyForBlankQuery() {
    ResponseEntity<Page<Document>> response = controller.searchDocuments("", 0, 10);
    assertTrue(response.getBody().isEmpty());
  }

  private Document makeDocument(String title, String content) {
    Document doc = new Document();
    doc.setTitle(title);
    doc.setContent(content);
    doc.setMetadata(Map.of());
    return doc;
  }

  private static class RecordingIndexService extends IndexService {
    UUID lastIndexedId;
    UUID lastUpdatedId;
    String deletedVectorId;

    RecordingIndexService() {
      super(null, null, null);
    }

    @Override
    public Document indexDocument(Document document) {
      document.setIndexed(true);
      if (document.getVectorId() == null) {
        document.setVectorId("vector-" + document.getId());
      }
      lastIndexedId = document.getId();
      return document;
    }

    @Override
    public Document updateDocumentIndex(Document document) {
      lastUpdatedId = document.getId();
      return indexDocument(document);
    }

    @Override
    public boolean deleteDocumentVector(String vectorId) {
      deletedVectorId = vectorId;
      return true;
    }

    @Override
    public void initializeIndex() {}

    @Override
    public List<Map.Entry<UUID, Double>> findSimilarDocuments(
        List<Double> queryVector, int limit, double minScore) {
      return List.of(new AbstractMap.SimpleEntry<>(UUID.randomUUID(), 1.0));
    }
  }
}
