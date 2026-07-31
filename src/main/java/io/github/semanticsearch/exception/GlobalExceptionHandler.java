package io.github.semanticsearch.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;

/** Maps exceptions to consistent JSON error responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** Validation failures on an {@code @Valid} request body. */
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public Map<String, Object> handleValidationExceptions(MethodArgumentNotValidException ex) {
    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            error -> {
              String fieldName = ((FieldError) error).getField();
              errors.put(fieldName, error.getDefaultMessage());
            });

    Map<String, Object> response = body(HttpStatus.BAD_REQUEST, "Validation failed");
    response.put("errors", errors);
    return response;
  }

  /**
   * Validation failures on {@code @RequestParam} and {@code @PathVariable} arguments.
   *
   * <p>These arrive as ConstraintViolationException rather than MethodArgumentNotValidException,
   * and so need their own handler: without one they reach the catch-all below, which reports a
   * plain client mistake such as {@code limit=0} as 500 Internal Server Error.
   */
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(ConstraintViolationException.class)
  public Map<String, Object> handleConstraintViolation(ConstraintViolationException ex) {
    Map<String, String> errors = new LinkedHashMap<>();
    ex.getConstraintViolations()
        .forEach(
            violation ->
                errors.put(
                    lastNode(violation.getPropertyPath().toString()), violation.getMessage()));

    Map<String, Object> response = body(HttpStatus.BAD_REQUEST, "Validation failed");
    response.put("errors", errors);
    return response;
  }

  /** A required query parameter was absent. */
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public Map<String, Object> handleMissingParameter(MissingServletRequestParameterException ex) {
    return body(
        HttpStatus.BAD_REQUEST, "Required parameter '" + ex.getParameterName() + "' is missing");
  }

  /** A parameter could not be converted, for example a malformed UUID. */
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public Map<String, Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    return body(
        HttpStatus.BAD_REQUEST,
        "Parameter '" + ex.getName() + "' has an invalid value: " + ex.getValue());
  }

  /** Malformed JSON, or a field the request body does not define. */
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public Map<String, Object> handleUnreadableBody(HttpMessageNotReadableException ex) {
    return body(HttpStatus.BAD_REQUEST, rootMessage(ex));
  }

  /**
   * Nothing is mapped at this path.
   *
   * <p>Spring raises these for an unmatched route or a missing static resource. Both extend
   * ServletException, which the catch-all below would otherwise report as 500 Internal Server
   * Error, making every missing route and unresolved browser asset look like a server fault.
   */
  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
  public Map<String, Object> handleNotFound(Exception ex) {
    return body(HttpStatus.NOT_FOUND, "No endpoint or resource found for this path");
  }

  /** Content that already exists under another document id. */
  @ResponseStatus(HttpStatus.CONFLICT)
  @ExceptionHandler(DuplicateContentException.class)
  public Map<String, Object> handleDuplicateContent(DuplicateContentException ex) {
    return body(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Object> handleResponseStatusException(ResponseStatusException ex) {
    Map<String, Object> response = new HashMap<>();
    response.put("timestamp", LocalDateTime.now());
    response.put("status", ex.getStatusCode().value());
    response.put("error", ex.getStatusCode().toString());
    response.put("message", ex.getReason());
    return new ResponseEntity<>(response, ex.getStatusCode());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> handleAllExceptions(Exception ex, WebRequest request) {
    log.error("Unhandled exception", ex);

    Map<String, Object> response =
        body(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    response.put("path", request.getDescription(false));
    return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private static Map<String, Object> body(HttpStatus status, String message) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("timestamp", LocalDateTime.now());
    response.put("status", status.value());
    response.put("error", status.getReasonPhrase());
    response.put("message", message);
    return response;
  }

  /** "search.query" -> "query", so the client sees the parameter name it sent. */
  private static String lastNode(String propertyPath) {
    int lastDot = propertyPath.lastIndexOf('.');
    return lastDot < 0 ? propertyPath : propertyPath.substring(lastDot + 1);
  }

  private static String rootMessage(Throwable throwable) {
    Throwable cause = throwable;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    String message = cause.getMessage();
    return message == null ? "Malformed request body" : message.split("\n")[0];
  }
}
