package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.service.OpenAiService;

@ExtendWith(MockitoExtension.class)
public class EmbeddingServiceTest {

  @Mock private OpenAiService openAiService;

  @InjectMocks private EmbeddingService embeddingService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(embeddingService, "embeddingModel", "text-embedding-ada-002");
  }

  @Test
  void testEmbed_Success() {
    // Arrange
    String text = "Test text for embedding";

    // Create a mock embedding with Float values (as per OpenAI SDK)
    List<Double> expectedEmbedding = Arrays.asList(0.1, 0.2, 0.3);

    // Create a mock embedding object with proper conversion
    Embedding embedding = mock(Embedding.class);
    // The OpenAI SDK returns List<Double> in newer versions
    when(embedding.getEmbedding()).thenReturn(expectedEmbedding);

    // Create the embedding result
    com.theokanning.openai.embedding.EmbeddingResult embeddingResult =
        mock(com.theokanning.openai.embedding.EmbeddingResult.class);
    when(embeddingResult.getData()).thenReturn(Collections.singletonList(embedding));

    // Mock the OpenAI service response
    when(openAiService.createEmbeddings(any(EmbeddingRequest.class))).thenReturn(embeddingResult);

    // Act
    List<Double> result = embeddingService.embed(text);

    // Assert
    assertEquals(expectedEmbedding, result);
    verify(openAiService).createEmbeddings(any(EmbeddingRequest.class));
  }

  @Test
  void testEmbed_EmptyResponse() {
    // Arrange
    String text = "Test text for embedding";

    com.theokanning.openai.embedding.EmbeddingResult embeddingResult =
        mock(com.theokanning.openai.embedding.EmbeddingResult.class);
    when(embeddingResult.getData()).thenReturn(Collections.emptyList());

    when(openAiService.createEmbeddings(any(EmbeddingRequest.class))).thenReturn(embeddingResult);

    // Act
    List<Double> result = embeddingService.embed(text);

    // Assert
    assertTrue(result.isEmpty());
    verify(openAiService).createEmbeddings(any(EmbeddingRequest.class));
  }

  @Test
  void testEmbed_Exception() {
    // Arrange
    String text = "Test text for embedding";

    // Mock the exception with a specific type that matches the fallback method signature
    when(openAiService.createEmbeddings(any(EmbeddingRequest.class)))
        .thenThrow(new RuntimeException("API Error"));

    // Since resilience4j annotations aren't active in test context,
    // we need to directly test the fallback method
    try {
      // This will throw the exception since annotations aren't active
      List<Double> result = embeddingService.embed(text);
      fail("Expected exception was not thrown");
    } catch (RuntimeException e) {
      // Manually invoke the fallback method using reflection
      try {
        java.lang.reflect.Method fallbackMethod =
            EmbeddingService.class.getDeclaredMethod(
                "fallbackEmbed", String.class, Exception.class);
        fallbackMethod.setAccessible(true);
        List<Double> fallbackResult =
            (List<Double>)
                fallbackMethod.invoke(embeddingService, text, new RuntimeException("API Error"));

        // Assert the fallback behavior
        assertTrue(fallbackResult.isEmpty());
      } catch (Exception reflectionEx) {
        fail("Failed to invoke fallback method: " + reflectionEx.getMessage());
      }
    }

    // Verify the service was called
    verify(openAiService).createEmbeddings(any(EmbeddingRequest.class));
  }
}
