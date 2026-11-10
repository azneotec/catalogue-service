package io.github.azneotec.ticketing.catalogue.adapter.rest;

import io.github.azneotec.ticketing.catalogue.application.ConcurrentUpdateException;
import io.github.azneotec.ticketing.catalogue.application.NotFoundException;
import io.github.azneotec.ticketing.catalogue.domain.DomainException;
import io.github.azneotec.ticketing.catalogue.domain.IllegalTransitionException;
import io.github.azneotec.ticketing.catalogue.domain.InvalidValueException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into Problem Details (RFC 9457) responses. The base class already does this for
 * Spring MVC's own errors (unreadable JSON, wrong parameter type, 405, ...); this class adds the
 * domain and application exceptions and lists every field problem for validation errors.
 *
 * <p>Status codes:
 *
 * <ul>
 *   <li>400 a value breaks a rule or a request is malformed
 *   <li>404 the event or venue does not exist
 *   <li>409 the event's state forbids the change (e.g. publishing a cancelled event)
 *   <li>412 the {@code If-Match} version is stale: someone changed the event since the client read
 *       it
 *   <li>428 {@code If-Match} is missing on a state-changing call
 * </ul>
 */
@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

  /**
   * {@link DomainException} is sealed, so this switch has no {@code default}: a new domain
   * exception does not compile until someone decides its HTTP status here.
   */
  @ExceptionHandler(DomainException.class)
  ProblemDetail domain(DomainException exception) {
    return switch (exception) {
      case InvalidValueException invalid ->
          Problems.of(
              HttpStatus.BAD_REQUEST, "invalid-value", "Invalid value", invalid.getMessage());
      case IllegalTransitionException illegal ->
          Problems.of(
              HttpStatus.CONFLICT,
              "illegal-transition",
              "Change not allowed in the current state",
              illegal.getMessage());
    };
  }

  @ExceptionHandler(NotFoundException.class)
  ProblemDetail notFound(NotFoundException exception) {
    return Problems.of(HttpStatus.NOT_FOUND, "not-found", "Not found", exception.getMessage());
  }

  /**
   * Every state-changing call carries {@code If-Match}, so a version conflict always means the
   * client's precondition failed: 412, not 409. 409 stays for business-rule conflicts, so a client
   * can tell "reload and retry" apart from "this will never work".
   */
  @ExceptionHandler(ConcurrentUpdateException.class)
  ProblemDetail stale(ConcurrentUpdateException exception) {
    return Problems.of(
        HttpStatus.PRECONDITION_FAILED,
        "stale-version",
        "The event has changed",
        exception.getMessage());
  }

  /** A {@code @Valid} request body failed: list every field, not only the first. */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    var errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> fieldError(error.getField(), error.getDefaultMessage()))
            .toList();
    return handleExceptionInternal(exception, validationProblem(errors), headers, status, request);
  }

  /** A constraint on a request parameter failed, e.g. {@code size=500}. */
  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    var errors =
        exception.getParameterValidationResults().stream()
            .flatMap(result ->
                result.getResolvableErrors().stream()
                    .map(error ->
                        fieldError(
                            result.getMethodParameter().getParameterName(),
                            error.getDefaultMessage())))
            .toList();
    return handleExceptionInternal(
        exception, validationProblem(errors), headers, HttpStatus.BAD_REQUEST, request);
  }

  private static ProblemDetail validationProblem(List<Map<String, String>> errors) {
    var problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request has invalid fields");
    problem.setType(URI.create(Problems.TYPE_PREFIX + "validation"));
    problem.setTitle("Validation failed");
    problem.setProperty("errors", errors);
    return problem;
  }

  private static Map<String, String> fieldError(String field, String message) {
    return Map.of("field", String.valueOf(field), "message", String.valueOf(message));
  }
}
