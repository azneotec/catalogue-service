package io.github.azneotec.ticketing.catalogue.domain;

import java.io.Serial;

/** A value broke an invariant, e.g. a blank name or a duplicate seat. */
public final class InvalidValueException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  public InvalidValueException(String message) {
    super(message);
  }
}
