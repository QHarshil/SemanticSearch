package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.semanticsearch.model.Document;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import jakarta.json.stream.JsonGenerator;

/**
 * Serialises the indexed document body through the same mapper the Elasticsearch transport uses.
 *
 * <p>This is the cheap half of the Elasticsearch coverage: it needs no container, so it runs
 * everywhere, and it catches the failure mode that matters most about this map - a value the mapper
 * cannot introspect serialises to {@code {}}, which Elasticsearch rejects while parsing
 * dense_vector because it expects an array of numbers. Wrapping the vector in {@code JsonData} does
 * exactly that.
 */
class IndexedSourceSerializationTest {

  private String serialize(Map<String, Object> source) {
    JacksonJsonpMapper mapper = new JacksonJsonpMapper();
    StringWriter written = new StringWriter();
    try (JsonGenerator generator = mapper.jsonProvider().createGenerator(written)) {
      mapper.serialize(source, generator);
    }
    return written.toString();
  }

  private Document document(Map<String, String> metadata) {
    Document document = new Document();
    document.setId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
    document.setTitle("Ranking Signals");
    document.setContent("Ranking blends similarity and boosts.");
    document.setContentHash("hash");
    document.setMetadata(metadata);
    return document;
  }

  @Test
  void theVectorIsWrittenAsAnArrayOfNumbers() {
    String json = serialize(IndexService.sourceOf(document(Map.of()), List.of(0.5, -0.25, 0.125)));

    assertTrue(
        json.contains("\"vector\":[0.5,-0.25,0.125]"),
        "dense_vector needs a numeric array, got " + json);
  }

  @Test
  void theDocumentIdIsWrittenSoResultsCanBeResolvedBackToRows() {
    String json = serialize(IndexService.sourceOf(document(Map.of()), List.of(1.0)));

    assertTrue(
        json.contains("\"document_id\":\"11111111-2222-3333-4444-555555555555\""),
        "retrieval reads document_id off each hit, got " + json);
  }

  @Test
  void metadataIsWrittenAsLowerCasedFieldsSoTermFiltersMatchCaseInsensitively() {
    String json =
        serialize(IndexService.sourceOf(document(Map.of("topic", "Ranking")), List.of(1.0)));

    assertTrue(
        json.contains("\"metadata\":{\"topic\":\"ranking\"}"),
        "a flattened field matches terms exactly, so values are lower-cased, got " + json);
  }

  @Test
  void emptyMetadataIsWrittenAsAnEmptyObjectRatherThanOmitted() {
    String json = serialize(IndexService.sourceOf(document(Map.of()), List.of(1.0)));

    assertTrue(json.contains("\"metadata\":{}"), json);
  }
}
