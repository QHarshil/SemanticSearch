package io.github.semanticsearch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.github.semanticsearch.service.IndexService;

/**
 * Initializes the Elasticsearch index on application startup to avoid missing mappings in fresh
 * environments.
 */
@Component
public class IndexInitializationConfig {

  private static final Logger log = LoggerFactory.getLogger(IndexInitializationConfig.class);

  private final IndexService indexService;

  @Value("${elasticsearch.index.auto-init:true}")
  private boolean autoInit;

  public IndexInitializationConfig(IndexService indexService) {
    this.indexService = indexService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void initializeIndexOnStartup() {
    if (!autoInit) {
      log.info("Skipping Elasticsearch index initialization (elasticsearch.index.auto-init=false)");
      return;
    }

    try {
      indexService.initializeIndex();
    } catch (Exception e) {
      log.error("Failed to initialize Elasticsearch index on startup", e);
    }
  }
}
