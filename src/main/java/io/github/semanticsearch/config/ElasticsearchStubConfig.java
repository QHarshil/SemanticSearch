package io.github.semanticsearch.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.Endpoint;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.TransportOptions;

/**
 * Provides a lightweight stub Elasticsearch client when {@code elasticsearch.stub-enabled=true}.
 * This satisfies dependency injection while IndexService routes to its in-memory fallback.
 */
@Configuration
@ConditionalOnProperty(name = "elasticsearch.stub-enabled", havingValue = "true")
public class ElasticsearchStubConfig {

  @Bean
  public ElasticsearchClient elasticsearchClient() {
    return new ElasticsearchClient(new NoOpElasticsearchTransport());
  }

  private static class NoOpElasticsearchTransport implements ElasticsearchTransport {
    private final JsonpMapper mapper = new JacksonJsonpMapper();

    @Override
    public <RequestT, ResponseT, ErrorT> ResponseT performRequest(
        RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, TransportOptions options) {
      throw new UnsupportedOperationException(
          "Elasticsearch stub is active; no remote transport available for endpoint " + endpoint.id());
    }

    @Override
    public <RequestT, ResponseT, ErrorT> java.util.concurrent.CompletableFuture<ResponseT>
        performRequestAsync(
            RequestT request,
            Endpoint<RequestT, ResponseT, ErrorT> endpoint,
            TransportOptions options) {
      throw new UnsupportedOperationException(
          "Elasticsearch stub is active; no remote transport available for endpoint " + endpoint.id());
    }

    @Override
    public JsonpMapper jsonpMapper() {
      return mapper;
    }

    @Override
    public TransportOptions options() {
      return null;
    }

    @Override
    public void close() {}
  }
}
