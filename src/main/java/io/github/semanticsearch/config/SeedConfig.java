package io.github.semanticsearch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.semanticsearch.service.SeedService;

@Configuration
public class SeedConfig {

  @Bean
  CommandLineRunner seedRunner(SeedService seedService, @Value("${seed.demo.enabled:false}") boolean enabled) {
    return args -> {
      if (enabled) {
        seedService.seedDemoDocuments();
      }
    };
  }
}
