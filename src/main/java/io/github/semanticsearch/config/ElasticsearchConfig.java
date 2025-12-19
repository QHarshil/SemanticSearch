package io.github.semanticsearch.config;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;

/**
 * Configuration for Elasticsearch client. Sets up the Elasticsearch client with connection details
 * and authentication.
 */
@Configuration
@ConditionalOnProperty(name = "elasticsearch.stub-enabled", havingValue = "false", matchIfMissing = true)
public class ElasticsearchConfig {

  @Value("${elasticsearch.host}")
  private String host;

  @Value("${elasticsearch.port}")
  private int port;

  @Value("${elasticsearch.protocol:http}")
  private String protocol;

  @Value("${elasticsearch.username:}")
  private String username;

  @Value("${elasticsearch.password:}")
  private String password;

  /**
   * Creates and configures the Elasticsearch client.
   *
   * @return Configured ElasticsearchClient
   */
  @Bean
  public ElasticsearchClient elasticsearchClient() {
    final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

    // Add authentication if credentials are provided
    if (!username.isEmpty() && !password.isEmpty()) {
      credentialsProvider.setCredentials(
          AuthScope.ANY, new UsernamePasswordCredentials(username, password));
    }

    // Create the low-level client
    RestClient restClient =
        RestClient.builder(new HttpHost(host, port, protocol))
            .setHttpClientConfigCallback(
                httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
            .build();

    // Create the transport with the Jackson mapper
    ElasticsearchTransport transport =
        new RestClientTransport(restClient, new JacksonJsonpMapper());

    // Create the API client
    return new ElasticsearchClient(transport);
  }
}
