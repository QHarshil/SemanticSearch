package io.github.semanticsearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for the Semantic Search service. Enables Spring Boot auto-configuration,
 * JPA auditing, caching, and async processing.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
@EnableAsync
@ConfigurationPropertiesScan
public class SemanticSearchApplication {

  public static void main(String[] args) {
    SpringApplication.run(SemanticSearchApplication.class, args);
  }
}
