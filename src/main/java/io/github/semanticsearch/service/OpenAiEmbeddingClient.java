package io.github.semanticsearch.service;

import java.time.Duration;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal client for the OpenAI embeddings endpoint.
 *
 * <p>Deliberately hand-rolled rather than using an SDK. The only call this service needs is a
 * single POST, where a community SDK such as com.theokanning.openai-gpt3-java would pull in the
 * Scala standard library, RxJava and classgraph to support function-calling features never used
 * here, roughly 15 MB of the packaged jar.
 */
public class OpenAiEmbeddingClient {

  private final RestClient restClient;
  private final String model;
  private final Integer dimensions;

  /**
   * @param dimensions requested output size, or null for the model default. The text-embedding-3
   *     models support shortening natively, which keeps the vector width consistent with the search
   *     index mapping.
   */
  public OpenAiEmbeddingClient(
      String baseUrl, String apiKey, String model, Integer dimensions, Duration timeout) {
    this.model = model;
    this.dimensions = dimensions;

    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout((int) timeout.toMillis());
    requestFactory.setReadTimeout((int) timeout.toMillis());

    this.restClient =
        RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .requestFactory(requestFactory)
            .build();
  }

  /**
   * @return the embedding for {@code text}, or an empty list if the response carried no data
   * @throws org.springframework.web.client.RestClientException if the call fails
   */
  public List<Double> embed(String text) {
    EmbeddingResponse response =
        restClient
            .post()
            .uri("/v1/embeddings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new EmbeddingRequest(model, text, dimensions))
            .retrieve()
            .body(EmbeddingResponse.class);

    if (response == null || response.data() == null || response.data().isEmpty()) {
      return List.of();
    }
    return response.data().get(0).embedding();
  }

  /** {@code dimensions} is omitted when null; the API rejects an explicit null. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record EmbeddingRequest(String model, String input, Integer dimensions) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record EmbeddingResponse(List<EmbeddingData> data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record EmbeddingData(List<Double> embedding) {}
}
