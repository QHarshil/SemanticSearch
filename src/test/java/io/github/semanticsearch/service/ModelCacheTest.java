package io.github.semanticsearch.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

/**
 * Covers the guarantees {@link ModelCache} exists to provide: a file that does not match its digest
 * never reaches the model loader, and a file already in the cache is not fetched again.
 *
 * <p>Served from a loopback HTTP server so the download path runs for real without depending on
 * Hugging Face being reachable.
 */
class ModelCacheTest {

  private static final byte[] PAYLOAD = "pretend this is a model".getBytes(StandardCharsets.UTF_8);

  @TempDir Path cacheDir;

  private HttpServer server;
  private AtomicInteger requests;
  private URI source;

  @BeforeEach
  void startServer() throws IOException {
    requests = new AtomicInteger();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/model",
        exchange -> {
          requests.incrementAndGet();
          exchange.sendResponseHeaders(200, PAYLOAD.length);
          try (OutputStream body = exchange.getResponseBody()) {
            body.write(PAYLOAD);
          }
        });
    server.createContext(
        "/missing",
        exchange -> {
          requests.incrementAndGet();
          exchange.sendResponseHeaders(404, -1);
          exchange.close();
        });
    server.start();
    source = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/model");
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void downloadsAFileThatIsNotYetCached() throws IOException {
    Path resolved = cache(true).resolve("model.bin", source, digest(PAYLOAD));

    assertArrayEquals(PAYLOAD, Files.readAllBytes(resolved));
    assertEquals(1, requests.get());
  }

  @Test
  void servesASecondCallFromDiskWithoutFetchingAgain() {
    ModelCache cache = cache(true);
    Path first = cache.resolve("model.bin", source, digest(PAYLOAD));
    Path second = cache.resolve("model.bin", source, digest(PAYLOAD));

    assertEquals(first, second);
    assertEquals(1, requests.get(), "a cached file was downloaded twice");
  }

  @Test
  void rejectsADownloadThatDoesNotMatchTheExpectedDigest() {
    // A mirror serving something else, or a proxy returning an error page with a
    // 200, both arrive as a file that loads and produces vectors nobody measured.
    String wrong = digest("a different model".getBytes(StandardCharsets.UTF_8));

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class, () -> cache(true).resolve("model.bin", source, wrong));

    assertTrue(thrown.getMessage().contains("SHA-256"), thrown.getMessage());
  }

  @Test
  void rejectsACachedFileThatNoLongerMatchesItsDigest() throws IOException {
    Path resolved = cache(true).resolve("model.bin", source, digest(PAYLOAD));
    Files.writeString(resolved, "swapped out from under us");

    assertThrows(
        IllegalStateException.class,
        () -> cache(true).resolve("model.bin", source, digest(PAYLOAD)));
  }

  @Test
  void refusesToDownloadWhenAutoDownloadIsOff() {
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> cache(false).resolve("model.bin", source, digest(PAYLOAD)));

    assertTrue(thrown.getMessage().contains("auto-download"), thrown.getMessage());
    assertEquals(0, requests.get());
  }

  @Test
  void reportsAFailedDownloadRatherThanCachingTheError() {
    URI missing = source.resolve("/missing");

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> cache(true).resolve("model.bin", missing, digest(PAYLOAD)));

    assertTrue(thrown.getMessage().contains("404"), thrown.getMessage());
    assertFalse(
        Files.exists(cacheDir.resolve("model.bin")),
        "a failed download must not leave a file the next run treats as a cache hit");
  }

  private ModelCache cache(boolean autoDownload) {
    return new ModelCache(cacheDir, autoDownload);
  }

  private static String digest(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }
}
