package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
class EmbeddingServiceTest {

  @Autowired private EmbeddingService embeddingService;

  @Value("${embedding.stub-dimensions}")
  private int stubDimensions;

  @Test
  void stubEmbeddingIsDeterministicAndSized() {
    List<Double> first = embeddingService.embed("semantic search works");
    List<Double> second = embeddingService.embed("semantic search works");

    assertFalse(first.isEmpty());
    assertEquals(stubDimensions, first.size());
    assertEquals(first, second, "Stub embeddings should be deterministic for the same input");
  }

  @Test
  void differentInputsYieldDifferentVectors() {
    List<Double> alpha = embeddingService.embed("alpha");
    List<Double> beta = embeddingService.embed("beta");

    assertNotEquals(alpha, beta, "Distinct inputs should not map to the same stub vector");
  }
}
