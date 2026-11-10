package io.github.azneotec.ticketing.catalogue.application;

import java.io.Serial;

/**
 * Someone else changed the aggregate after we read it (optimistic locking). Thrown by repository
 * adapters, which translate their own technology's error into this, so use cases and the REST layer
 * never see JPA or Spring types. The usual answer is: reload and retry; over HTTP, 412 Precondition
 * Failed.
 */
public final class ConcurrentUpdateException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public ConcurrentUpdateException(String what, Object id) {
    this(what, id, null);
  }

  public ConcurrentUpdateException(String what, Object id, Throwable cause) {
    super(what + " " + id + " was changed by someone else; reload and retry", cause);
  }
}
