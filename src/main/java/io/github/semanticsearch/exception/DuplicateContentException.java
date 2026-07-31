package io.github.semanticsearch.exception;

/**
 * Raised when a write would store content that already exists under a different document id.
 *
 * <p>Content is deduplicated on a hash of the body, enforced by a unique constraint. This exception
 * lets the write path report the collision as a conflict without knowing about HTTP; {@link
 * GlobalExceptionHandler} maps it to 409.
 */
public class DuplicateContentException extends RuntimeException {

  public DuplicateContentException(String message) {
    super(message);
  }

  public DuplicateContentException(String message, Throwable cause) {
    super(message, cause);
  }
}
