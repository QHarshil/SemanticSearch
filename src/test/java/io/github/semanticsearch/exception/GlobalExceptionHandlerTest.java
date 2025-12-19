package io.github.semanticsearch.exception;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

  @Test
  void handleValidationExceptionsCollectsErrors() throws NoSuchMethodException {
    BeanPropertyBindingResult bindingResult =
        new BeanPropertyBindingResult(new TestDto(), "testDto");
    bindingResult.addError(new FieldError("testDto", "field1", "error message 1"));
    bindingResult.addError(new FieldError("testDto", "field2", "error message 2"));

    Method method = TestDto.class.getDeclaredMethod("validate", String.class);
    MethodArgumentNotValidException exception =
        new MethodArgumentNotValidException(
            new org.springframework.core.MethodParameter(method, 0), bindingResult);

    Map<String, Object> response = exceptionHandler.handleValidationExceptions(exception);

    assertEquals(HttpStatus.BAD_REQUEST.value(), response.get("status"));
    @SuppressWarnings("unchecked")
    Map<String, String> errors = (Map<String, String>) response.get("errors");
    assertEquals(2, errors.size());
    assertEquals("error message 1", errors.get("field1"));
    assertEquals("error message 2", errors.get("field2"));
  }

  @Test
  void handleResponseStatusExceptionBuildsBody() {
    ResponseStatusException ex =
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");

    var response = exceptionHandler.handleResponseStatusException(ex);

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) response.getBody();
    assertEquals(HttpStatus.NOT_FOUND.value(), body.get("status"));
    assertEquals("Resource not found", body.get("message"));
  }

  @Test
  void handleAllExceptionsReturns500() {
    Exception ex = new RuntimeException("Unexpected error");
    MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    servletRequest.setRequestURI("/test");
    WebRequest request = new ServletWebRequest(servletRequest);

    var response = exceptionHandler.handleAllExceptions(ex, request);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) response.getBody();
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), body.get("status"));
    assertEquals("Internal Server Error", body.get("error"));
    assertEquals("An unexpected error occurred", body.get("message"));
    assertTrue(body.get("path").toString().contains("/test"));
  }

  private static class TestDto {
    @SuppressWarnings("unused")
    void validate(String input) {
      // No-op helper for MethodParameter creation
    }
  }
}
