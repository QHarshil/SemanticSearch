package io.github.semanticsearch.service;

import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.service.OpenAiService;
import io.github.semanticsearch.util.ResilienceUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmbeddingServiceTest {

    @Mock
    private OpenAiService openAiService;

    @InjectMocks
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(embeddingService, "embeddingModel", "text-embedding-ada-002");
    }

    @Test
    void testEmbed_Success() {
        // Arrange
        String text = "Test text for embedding";
        List<Float> expectedEmbedding = List.of(0.1f, 0.2f, 0.3f);
        
        Embedding embedding = new Embedding();
        embedding.setEmbedding(expectedEmbedding);
        
        com.theokanning.openai.embedding.EmbeddingResult embeddingResult = new com.theokanning.openai.embedding.EmbeddingResult();
        embeddingResult.setData(Collections.singletonList(embedding));
        
        when(openAiService.createEmbeddings(any(EmbeddingRequest.class))).thenReturn(embeddingResult);
        
        // Act
        List<Float> result = embeddingService.embed(text);
        
        // Assert
        assertEquals(expectedEmbedding, result);
        verify(openAiService).createEmbeddings(any(EmbeddingRequest.class));
    }

    @Test
    void testEmbed_EmptyResponse() {
        // Arrange
        String text = "Test text for embedding";
        
        com.theokanning.openai.embedding.EmbeddingResult embeddingResult = new com.theokanning.openai.embedding.EmbeddingResult();
        embeddingResult.setData(Collections.emptyList());
        
        when(openAiService.createEmbeddings(any(EmbeddingRequest.class))).thenReturn(embeddingResult);
        
        // Act
        List<Float> result = embeddingService.embed(text);
        
        // Assert
        assertTrue(result.isEmpty());
        verify(openAiService).createEmbeddings(any(EmbeddingRequest.class));
    }

    @Test
    void testEmbed_Exception() {
        // Arrange
        String text = "Test text for embedding";
        
        when(openAiService.createEmbeddings(any(EmbeddingRequest.class))).thenThrow(new RuntimeException("API Error"));
        
        // Act & Assert
        List<Float> result = embeddingService.embed(text);
        
        // Verify fallback returns empty list
        assertTrue(result.isEmpty());
        verify(openAiService).createEmbeddings(any(EmbeddingRequest.class));
    }
}
