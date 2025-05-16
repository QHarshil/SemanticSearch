package io.github.semanticsearch.controller;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;
import io.github.semanticsearch.service.IndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@WebFluxTest(DocumentController.class)
public class DocumentControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private IndexService indexService;

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
        
        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(inputDocument)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Document.class);
        
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
        webTestClient.post()
                .uri("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(inputDocument)
                .exchange()
                .expectStatus().isEqualTo(409);
        
        verify(documentRepository).findByContentHash(anyString());
        verify(documentRepository, never()).save(any(Document.class));
        verify(indexService, never()).indexDocument(any(Document.class));
    }

    @Test
    void testGetDocument_Success() {
        // Arrange
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(testDocument));
        
        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/documents/{id}", documentId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Document.class)
                .isEqualTo(testDocument);
        
        verify(documentRepository).findById(documentId);
    }

    @Test
    void testGetDocument_NotFound() {
        // Arrange
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());
        
        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/documents/{id}", documentId)
                .exchange()
                .expectStatus().isNotFound();
        
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
        
        // Act & Assert
        webTestClient.put()
                .uri("/api/v1/documents/{id}", documentId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateDocument)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Document.class);
        
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
        
        // Act & Assert
        webTestClient.delete()
                .uri("/api/v1/documents/{id}", documentId)
                .exchange()
                .expectStatus().isNoContent();
        
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
        
        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/documents?page=0&size=10&sort=createdAt&direction=DESC")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isArray()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.totalElements").isEqualTo(1);
        
        verify(documentRepository).findAll(any(PageRequest.class));
    }
}
