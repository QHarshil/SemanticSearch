package io.github.semanticsearch.config;

import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import io.micrometer.core.instrument.MeterRegistry;

/** Tags every metric with the application name and the active profile. */
@Configuration
public class MetricsConfig {

  @Bean
  public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(Environment environment) {
    // Property placeholders are not resolved inside a @Bean method body, so
    // "${spring.profiles.active:default}" written here ships verbatim as the
    // environment tag on every metric. The Environment resolves it.
    String[] activeProfiles = environment.getActiveProfiles();
    String environmentTag =
        activeProfiles.length == 0 ? "default" : String.join(",", activeProfiles);

    return registry ->
        registry
            .config()
            .commonTags("application", "semantic-search-java", "environment", environmentTag);
  }
}
