package io.github.semanticsearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * The application.
 *
 * <p>{@code @ConfigurationPropertiesScan} is what registers {@code SearchProperties}, {@code
 * OnnxProperties} and {@code BenchmarkProperties}. None of them is a bean on its own, so without
 * the scan every value silently falls back to its field initialiser.
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
