package io.github.semanticsearch.exception;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

public class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

  @Test
  void testHandleValidationExceptions() {
    // Arrange
    MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
    BindingResult bindingResult = mock(BindingResult.class);

    FieldError fieldError1 = new FieldError("object", "field1", "error message 1");
    FieldError fieldError2 = new FieldError("object", "field2", "error message 2");

    when(ex.getBindingResult()).thenReturn(bindingResult);
    when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError1, fieldError2));

    // Act
    Map<String, Object> response = exceptionHandler.handleValidationExceptions(ex);

    // Assert
    assertNotNull(response);
    assertEquals(HttpStatus.BAD_REQUEST.value(), response.get("status"));

    @SuppressWarnings("unchecked")
    Map<String, String> errors = (Map<String, String>) response.get("errors");
    assertNotNull(errors);
    assertEquals(2, errors.size());
    assertEquals("error message 1", errors.get("field1"));
    assertEquals("error message 2", errors.get("field2"));
  }

  @Test
  void testHandleResponseStatusException() {
    // Arrange
    ResponseStatusException ex =
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");

    // Act
    var response = exceptionHandler.handleResponseStatusException(ex);

    // Assert
    assertNotNull(response);
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) response.getBody();
    assertNotNull(body);
    assertEquals(HttpStatus.NOT_FOUND.value(), body.get("status"));
    assertEquals("Resource not found", body.get("message"));
  }

  @Test
  void testHandleAllExceptions() {
    // Arrange
    Exception ex = new RuntimeException("Unexpected error");
    WebRequest request = mock(WebRequest.class);
    when(request.getDescription(false)).thenReturn("uri=/test");

    // Act
    var response = exceptionHandler.handleAllExceptions(ex, request);

    // Assert
    assertNotNull(response);
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) response.getBody();
    assertNotNull(body);
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), body.get("status"));
    assertEquals("Internal Server Error", body.get("error"));
    assertEquals("An unexpected error occurred", body.get("message"));
    assertEquals("uri=/test", body.get("path"));
  }
}
