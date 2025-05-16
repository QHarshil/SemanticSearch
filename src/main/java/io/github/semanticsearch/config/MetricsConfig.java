package io.github.semanticsearch.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for metrics and monitoring.
 * Sets up Micrometer metrics collection and customization.
 */
@Configuration
public class MetricsConfig {

    /**
     * Customize the meter registry with common tags.
     *
     * @return MeterRegistryCustomizer for adding common tags
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags("application", "semantic-search-java")
                .commonTags("environment", "${spring.profiles.active:default}");
    }
}
