package io.github.azneotec.ticketing.catalogue.domain;

import java.io.Serial;

/** A lifecycle change that the current state does not allow, e.g. publishing a cancelled event. */
public final class IllegalTransitionException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  public IllegalTransitionException(String message) {
    super(message);
  }
}
