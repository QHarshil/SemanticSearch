package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.embedding.EmbeddingResult;
import com.theokanning.openai.service.OpenAiService;

class EmbeddingServiceTest {

  private StubOpenAiService openAiService;
  private EmbeddingService embeddingService;

  @BeforeEach
  void setUp() {
    openAiService = new StubOpenAiService();
    embeddingService = new EmbeddingService(openAiService);
    ReflectionTestUtils.setField(embeddingService, "embeddingModel", "text-embedding-ada-002");
  }

  @Test
  void embedReturnsVectorFromProvider() {
    List<Double> expected = Arrays.asList(0.1, 0.2, 0.3);
    Embedding embedding = new Embedding();
    embedding.setEmbedding(expected);
    EmbeddingResult result = new EmbeddingResult();
    result.setData(List.of(embedding));
    openAiService.nextResult = result;

    List<Double> vector = embeddingService.embed("Sample text");

    assertEquals(expected, vector);
    assertEquals("Sample text", openAiService.lastRequest.getInput().get(0));
  }

  @Test
  void embedReturnsEmptyListWhenProviderReturnsNothing() {
    EmbeddingResult result = new EmbeddingResult();
    result.setData(Collections.emptyList());
    openAiService.nextResult = result;

    List<Double> vector = embeddingService.embed("no data");

    assertTrue(vector.isEmpty());
  }

  @Test
  void fallbackReturnsEmptyListOnError() throws Exception {
    RuntimeException apiError = new RuntimeException("API Error");
    openAiService.toThrow = apiError;

    assertThrows(RuntimeException.class, () -> embeddingService.embed("boom"));

    Method fallback =
        EmbeddingService.class.getDeclaredMethod("fallbackEmbed", String.class, Exception.class);
    fallback.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<Double> result = (List<Double>) fallback.invoke(embeddingService, "boom", apiError);
    assertTrue(result.isEmpty());
  }

  private static class StubOpenAiService extends OpenAiService {
    EmbeddingResult nextResult;
    RuntimeException toThrow;
    EmbeddingRequest lastRequest;

    StubOpenAiService() {
      super("test-token");
    }

    @Override
    public EmbeddingResult createEmbeddings(EmbeddingRequest request) {
      this.lastRequest = request;
      if (toThrow != null) {
        throw toThrow;
      }
      return nextResult;
    }
  }
}
