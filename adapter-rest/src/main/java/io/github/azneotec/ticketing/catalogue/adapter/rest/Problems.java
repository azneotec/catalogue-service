package io.github.azneotec.ticketing.catalogue.adapter.rest;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Builds Problem Details (RFC 9457) bodies. Each problem has a stable {@code type} URI that clients
 * can switch on; it is a URN because it identifies the problem, it isn't a page to visit.
 */
final class Problems {

  static final String TYPE_PREFIX = "urn:ticketing:problem:";

  private Problems() {}

  static ProblemDetail of(HttpStatus status, String type, String title, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(TYPE_PREFIX + type));
    problem.setTitle(title);
    return problem;
  }

  /** An exception Spring MVC turns into exactly this problem response. */
  static ErrorResponseException exception(
      HttpStatus status, String type, String title, String detail) {
    return new ErrorResponseException(status, of(status, type, title, detail), null);
  }
}
