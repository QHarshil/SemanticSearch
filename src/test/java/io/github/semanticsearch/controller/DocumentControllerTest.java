package io.github.semanticsearch.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;
import io.github.semanticsearch.service.IndexService;

@ExtendWith(MockitoExtension.class)
public class DocumentControllerTest {

  @Mock private DocumentRepository documentRepository;

  @Mock private IndexService indexService;

  @InjectMocks private DocumentController documentController;

  private Document testDocument;
  private UUID documentId;

  @BeforeEach
  void setUp() {
    documentId = UUID.randomUUID();

    testDocument = new Document();
    testDocument.setId(documentId);
    testDocument.setTitle("Test Document");
    testDocument.setContent("This is a test document content");
    testDocument.setContentHash("test-hash");
    testDocument.setMetadata(Map.of("key", "value"));
  }

  @Test
  void testCreateDocument_Success() {
    // Arrange
    Document inputDocument = new Document();
    inputDocument.setTitle("New Document");
    inputDocument.setContent("This is new content");
    inputDocument.setMetadata(Map.of("key", "value"));

    when(documentRepository.findByContentHash(anyString())).thenReturn(Optional.empty());
    when(documentRepository.save(any(Document.class))).thenReturn(inputDocument);
    when(indexService.indexDocument(any(Document.class))).thenReturn(inputDocument);

    // Act
    ResponseEntity<Document> response = documentController.createDocument(inputDocument);

    // Assert
    assert response.getStatusCode() == HttpStatus.CREATED;
    assert response.getBody() == inputDocument;
    verify(documentRepository).findByContentHash(anyString());
    verify(documentRepository).save(any(Document.class));
    verify(indexService).indexDocument(any(Document.class));
  }

  @Test
  void testCreateDocument_DuplicateContent() {
    // Arrange
    Document inputDocument = new Document();
    inputDocument.setTitle("New Document");
    inputDocument.setContent("This is new content");

    when(documentRepository.findByContentHash(anyString())).thenReturn(Optional.of(testDocument));

    // Act & Assert
    try {
      documentController.createDocument(inputDocument);
      assert false : "Expected ResponseStatusException was not thrown";
    } catch (ResponseStatusException e) {
      assert e.getStatusCode() == HttpStatus.CONFLICT;
    }

    verify(documentRepository).findByContentHash(anyString());
    verify(documentRepository, never()).save(any(Document.class));
    verify(indexService, never()).indexDocument(any(Document.class));
  }

  @Test
  void testGetDocument_Success() {
    // Arrange
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(testDocument));

    // Act
    ResponseEntity<Document> response = documentController.getDocument(documentId);

    // Assert
    assert response.getStatusCode() == HttpStatus.OK;
    assert response.getBody() == testDocument;
    verify(documentRepository).findById(documentId);
  }

  @Test
  void testGetDocument_NotFound() {
    // Arrange
    when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

    // Act & Assert
    try {
      documentController.getDocument(documentId);
      assert false : "Expected ResponseStatusException was not thrown";
    } catch (ResponseStatusException e) {
      assert e.getStatusCode() == HttpStatus.NOT_FOUND;
    }

    verify(documentRepository).findById(documentId);
  }

  @Test
  void testUpdateDocument_Success() {
    // Arrange
    Document updateDocument = new Document();
    updateDocument.setTitle("Updated Title");
    updateDocument.setContent("Updated content");
    updateDocument.setMetadata(Map.of("key", "updated"));

    when(documentRepository.findById(documentId)).thenReturn(Optional.of(testDocument));
    when(documentRepository.save(any(Document.class))).thenReturn(testDocument);
    when(indexService.updateDocumentIndex(any(Document.class))).thenReturn(testDocument);

    // Act
    ResponseEntity<Document> response =
        documentController.updateDocument(documentId, updateDocument);

    // Assert
    assert response.getStatusCode() == HttpStatus.OK;
    assert response.getBody() == testDocument;
    verify(documentRepository).findById(documentId);
    verify(documentRepository).save(any(Document.class));
    verify(indexService).updateDocumentIndex(any(Document.class));
  }

  @Test
  void testDeleteDocument_Success() {
    // Arrange
    testDocument.setVectorId("test-vector-id");
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(testDocument));
    when(indexService.deleteDocumentVector(anyString())).thenReturn(true);
    doNothing().when(documentRepository).delete(any(Document.class));

    // Act
    ResponseEntity<Void> response = documentController.deleteDocument(documentId);

    // Assert
    assert response.getStatusCode() == HttpStatus.NO_CONTENT;
    verify(documentRepository).findById(documentId);
    verify(indexService).deleteDocumentVector("test-vector-id");
    verify(documentRepository).delete(testDocument);
  }

  @Test
  void testListDocuments_Success() {
    // Arrange
    List<Document> documents = Arrays.asList(testDocument);
    PageImpl<Document> page = new PageImpl<>(documents, PageRequest.of(0, 10), 1);

    when(documentRepository.findAll(any(PageRequest.class))).thenReturn(page);

    // Act
    ResponseEntity<org.springframework.data.domain.Page<Document>> response =
        documentController.listDocuments(0, 10, "createdAt", "DESC");

    // Assert
    assert response.getStatusCode() == HttpStatus.OK;
    assert response.getBody().getTotalElements() == 1;
    assert response.getBody().getContent().get(0) == testDocument;
    verify(documentRepository).findAll(any(PageRequest.class));
  }
}
