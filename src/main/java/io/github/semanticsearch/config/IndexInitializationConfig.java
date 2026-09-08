package io.github.semanticsearch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.github.semanticsearch.repository.DocumentRepository;
import io.github.semanticsearch.service.IndexService;

/**
 * Brings the search index up at startup, so a fresh environment has its mappings and a restart does
 * not leave the service answering from an index that is not there.
 */
@Component
public class IndexInitializationConfig {

  private static final Logger log = LoggerFactory.getLogger(IndexInitializationConfig.class);

  private final IndexService indexService;
  private final DocumentRepository documentRepository;

  @Value("${elasticsearch.index.auto-init:true}")
  private boolean autoInit;

  public IndexInitializationConfig(
      IndexService indexService, DocumentRepository documentRepository) {
    this.indexService = indexService;
    this.documentRepository = documentRepository;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void initializeIndexOnStartup() {
    if (!autoInit) {
      log.info("Skipping index initialization (elasticsearch.index.auto-init=false)");
      return;
    }

    try {
      indexService.initializeIndex();
      rebuildInMemoryIndex();
    } catch (Exception e) {
      log.error("Failed to initialize the search index on startup", e);
    }
  }

  /**
   * Re-embeds the stored corpus when the in-process index is the active one.
   *
   * <p>That index lives in the heap, so a restart empties it while the rows survive. Nothing else
   * notices: {@code reconcileUnindexed} looks for rows marked unindexed, and every row here is
   * marked indexed from before the restart. Search would keep answering, quietly reduced to
   * whatever BM25 alone can find, until somebody called the rebuild endpoint.
   *
   * <p>The cost is one embedding per passage at boot. Elasticsearch keeps its own vectors, so this
   * does not run for it.
   */
  private void rebuildInMemoryIndex() {
    if (!indexService.isInProcessIndexEmpty()) {
      return;
    }
    long stored = documentRepository.count();
    if (stored == 0) {
      return;
    }
    log.info("Rebuilding the in-process vector index over {} stored documents", stored);
    indexService.rebuildIndex();
  }
}
