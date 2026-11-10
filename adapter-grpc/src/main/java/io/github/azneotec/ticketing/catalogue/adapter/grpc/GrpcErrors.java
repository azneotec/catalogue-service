package io.github.azneotec.ticketing.catalogue.adapter.grpc;

import io.github.azneotec.ticketing.catalogue.application.ConcurrentUpdateException;
import io.github.azneotec.ticketing.catalogue.application.NotFoundException;
import io.github.azneotec.ticketing.catalogue.domain.DomainException;
import io.github.azneotec.ticketing.catalogue.domain.IllegalTransitionException;
import io.github.azneotec.ticketing.catalogue.domain.InvalidValueException;
import io.grpc.Status;

/**
 * Maps exceptions to gRPC status codes, the gRPC counterpart of the REST Problem Details mapping.
 * Unknown exceptions become {@code INTERNAL} without their message, so internals don't leak to
 * callers; they are logged on the server instead.
 */
final class GrpcErrors {

  private GrpcErrors() {}

  static Status toStatus(Throwable error) {
    return switch (error) {
      // DomainException is sealed: this inner switch needs no default, and a new domain exception
      // won't compile until it gets a status here.
      case DomainException domain ->
          switch (domain) {
            case InvalidValueException invalid ->
                Status.INVALID_ARGUMENT.withDescription(invalid.getMessage());
            case IllegalTransitionException illegal ->
                Status.FAILED_PRECONDITION.withDescription(illegal.getMessage());
          };
      case NotFoundException notFound -> Status.NOT_FOUND.withDescription(notFound.getMessage());
      // gRPC's code for "a concurrency conflict; retry the whole read-modify-write".
      case ConcurrentUpdateException conflict ->
          Status.ABORTED.withDescription(conflict.getMessage());
      case InvalidRequestException invalid ->
          Status.INVALID_ARGUMENT.withDescription(invalid.getMessage());
      default -> Status.INTERNAL.withDescription("internal error").withCause(error);
    };
  }

  /** A request field the server can't use, e.g. a malformed id or page token. */
  static final class InvalidRequestException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    InvalidRequestException(String message) {
      super(message);
    }
  }
}
