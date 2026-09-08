package io.github.semanticsearch.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A directory of large files kept on disk, fetched on first use and checked against a known digest.
 *
 * <p>Model weights and evaluation corpora are both too large to keep in the repository, so they are
 * downloaded instead. That makes the digest the only thing tying what runs to what was reviewed: a
 * mirror serving a different file, a truncated download and a half-written cache entry all produce
 * something that loads without complaint and gives results nobody has checked. Every file is hashed
 * on every use, not only after downloading, because a cached file can be replaced or corrupted
 * between runs.
 */
public final class VerifiedFileCache {

  private static final Logger log = LoggerFactory.getLogger(VerifiedFileCache.class);

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10);

  private final Path directory;
  private final boolean autoDownload;
  private final String downloadSetting;

  /**
   * @param downloadSetting the configuration key that turns downloading on, named in the error
   *     raised when a file is missing and downloading is off
   */
  public VerifiedFileCache(Path directory, boolean autoDownload, String downloadSetting) {
    this.directory = directory;
    this.autoDownload = autoDownload;
    this.downloadSetting = downloadSetting;
  }

  /**
   * Return the local path of a model file, downloading it if it is absent.
   *
   * @param fileName name the file is cached under
   * @param source where to fetch it from when the cache is empty
   * @param sha256 hex-encoded SHA-256 the file must match
   * @throws IllegalStateException if the file is absent and cannot be fetched, or if what is on
   *     disk does not match {@code sha256}
   */
  public Path resolve(String fileName, URI source, String sha256) {
    Path target = directory.resolve(fileName);

    if (!Files.isRegularFile(target)) {
      if (!autoDownload) {
        throw new IllegalStateException(
            target
                + " is missing and "
                + downloadSetting
                + " is false. Fetch it from "
                + source
                + " yourself, or point the cache directory at somewhere that already holds it.");
      }
      download(source, target, sha256);
    }

    String actual = sha256(target);
    if (!actual.equalsIgnoreCase(sha256)) {
      throw new IllegalStateException(
          target
              + " has SHA-256 "
              + actual
              + " but "
              + sha256
              + " was expected. Delete it to force a fresh download.");
    }
    return target;
  }

  private void download(URI source, Path target, String sha256) {
    // Downloaded beside the target, checked, and only then moved into place. A
    // file that arrives truncated or altered never becomes a cache entry, so the
    // next run downloads again instead of failing the same way forever on bytes
    // it cannot repair by itself.
    Path partial = target.resolveSibling(target.getFileName() + ".partial");
    log.info("Downloading {} to {}", source, target);
    try (HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()) {
      Files.createDirectories(target.getParent());
      HttpRequest request = HttpRequest.newBuilder(source).timeout(REQUEST_TIMEOUT).GET().build();
      HttpResponse<InputStream> response =
          client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            "Download of " + source + " returned HTTP " + response.statusCode());
      }
      try (InputStream body = response.body()) {
        Files.copy(body, partial, StandardCopyOption.REPLACE_EXISTING);
      }
      String actual = sha256(partial);
      if (!actual.equalsIgnoreCase(sha256)) {
        throw new IllegalStateException(
            source + " served content with SHA-256 " + actual + " but " + sha256 + " was expected");
      }
      move(partial, target);
      log.info("Downloaded {} ({} bytes)", target.getFileName(), Files.size(target));
    } catch (IOException e) {
      throw new IllegalStateException("Could not download " + source, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted downloading " + source, e);
    } finally {
      deleteQuietly(partial);
    }
  }

  private static void move(Path partial, Path target) throws IOException {
    try {
      Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      log.debug("Could not remove {}", path, e);
    }
  }

  private static String sha256(Path file) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream in = Files.newInputStream(file);
          DigestInputStream digesting = new DigestInputStream(in, digest)) {
        byte[] buffer = new byte[1 << 16];
        while (digesting.read(buffer) != -1) {
          // Reading is what feeds the digest.
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException e) {
      throw new IllegalStateException("Could not read " + file, e);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available in this JVM", e);
    }
  }
}
