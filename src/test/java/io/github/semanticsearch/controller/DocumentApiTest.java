package io.github.semanticsearch.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.semanticsearch.repository.DocumentRepository;
import io.github.semanticsearch.service.IndexService;

/**
 * Exercises the document API over HTTP against the real persistence and indexing path.
 *
 * <p>Nothing is substituted for the indexing or persistence layer. A fake IndexService whose
 * indexDocument sets {@code indexed=true} would let these assertions confirm that the fake ran
 * rather than that the document was indexed, which is the one thing worth checking here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private IndexService indexService;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void clearCorpus() {
    documentRepository.deleteAll();
    // The in-memory vector index is a singleton across the test context, so a
    // repository-level deleteAll would leave vectors behind pointing at rows
    // that no longer exist.
    indexService.rebuildIndex();
  }

  private String documentJson(String title, String content) {
    return """
        {"title":"%s","content":"%s","metadata":{"topic":"testing"}}
        """
        .formatted(title, content);
  }

  private String create(String title, String content) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/v1/documents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(documentJson(title, content)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(response).get("id").asText();
  }

  @Test
  void createReturns201AndIndexesTheDocument() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(documentJson("Vector Search", "Vector search compares embeddings.")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id", notNullValue()))
        .andExpect(jsonPath("$.title", is("Vector Search")))
        .andExpect(jsonPath("$.indexed", is(true)))
        .andExpect(jsonPath("$.vectorId", notNullValue()))
        .andExpect(jsonPath("$.contentHash", notNullValue()))
        .andExpect(jsonPath("$.createdAt", notNullValue()));
  }

  @Test
  void theServerComputesTheContentHashAndIgnoresAnyClientValue() throws Exception {
    String body =
        """
        {"title":"Hashing","content":"Content hash is derived server side.",
         "contentHash":"attacker-supplied"}
        """;

    mockMvc
        .perform(post("/api/v1/documents").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.contentHash", is(not("attacker-supplied"))));
  }

  @Test
  void identicalContentIsRejectedAsAConflict() throws Exception {
    create("First", "Exactly the same body text.");

    mockMvc
        .perform(
            post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(documentJson("Second", "Exactly the same body text.")))
        .andExpect(status().isConflict());
  }

  @Test
  void blankTitleIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(documentJson("", "Body text is fine.")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.title").exists());
  }

  @Test
  void blankContentIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(documentJson("Has a title", "")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.content").exists());
  }

  @Test
  void getByIdReturnsTheDocumentAnd404WhenAbsent() throws Exception {
    String id = create("Findable", "This document can be fetched by id.");

    mockMvc
        .perform(get("/api/v1/documents/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title", is("Findable")));

    mockMvc
        .perform(get("/api/v1/documents/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  @Test
  void malformedIdIsARequestErrorNotAServerError() throws Exception {
    mockMvc.perform(get("/api/v1/documents/{id}", "not-a-uuid")).andExpect(status().isBadRequest());
  }

  @Test
  void deleteRemovesTheDocument() throws Exception {
    String id = create("Disposable", "This document will be deleted.");

    mockMvc.perform(delete("/api/v1/documents/{id}", id)).andExpect(status().isNoContent());
    mockMvc.perform(get("/api/v1/documents/{id}", id)).andExpect(status().isNotFound());
    mockMvc.perform(delete("/api/v1/documents/{id}", id)).andExpect(status().isNotFound());
  }

  @Test
  void listReturnsAPageEnvelopeNotABareArray() throws Exception {
    create("One", "First document body.");
    create("Two", "Second document body.");

    // Clients iterate $.content, not the response root. Pinning the envelope shape
    // keeps a caller from treating the body itself as an array.
    mockMvc
        .perform(get("/api/v1/documents").param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()", is(1)))
        .andExpect(jsonPath("$.totalElements", is(2)))
        .andExpect(jsonPath("$.totalPages", is(2)));
  }

  @Test
  void listRejectsAnUnknownSortDirection() throws Exception {
    create("One", "First document body.");

    mockMvc
        .perform(get("/api/v1/documents").param("direction", "sideways"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void keywordSearchMatchesOnSubstring() throws Exception {
    create("Elasticsearch Guide", "How to run a kNN query against a dense vector field.");
    create("Unrelated", "Nothing to do with the other document.");

    mockMvc
        .perform(get("/api/v1/documents/search").param("text", "kNN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements", is(1)))
        .andExpect(jsonPath("$.content[0].title", is("Elasticsearch Guide")));
  }
}
