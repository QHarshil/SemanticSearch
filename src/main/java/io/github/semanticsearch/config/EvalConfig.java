package io.github.semanticsearch.config;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.semanticsearch.service.EvalService;
import io.github.semanticsearch.service.SeedService;

@Configuration
public class EvalConfig {

  private static final Logger log = LoggerFactory.getLogger(EvalConfig.class);

  @Bean
  CommandLineRunner evalRunner(
      EvalService evalService,
      SeedService seedService,
      @Value("${eval.run-on-startup:false}") boolean runOnStartup) {
    return args -> {
      if (!runOnStartup) {
        return;
      }
      log.info("Running evaluation at startup");
      seedService.seedDemoDocuments();
      var result = evalService.runCuratedEval(5);
      Path out = Path.of("target/eval/report.json");
      Files.createDirectories(out.getParent());
      new ObjectMapper().writeValue(out.toFile(), result);
      log.info("Eval completed: MRR={} NDCG={} Recall@5={}", result.mrr(), result.ndcg(), result.recallAtK());
    };
  }
}
