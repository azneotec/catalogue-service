package io.github.azneotec.ticketing.catalogue.domain;

import java.io.Serial;

/**
 * Base of every rule violation the domain reports. Sealed, so an adapter (e.g. the REST layer in
 * session 007) can map each case to a response with an exhaustive switch.
 */
public abstract sealed class DomainException extends RuntimeException
    permits InvalidValueException, IllegalTransitionException {

  @Serial private static final long serialVersionUID = 1L;

  protected DomainException(String message) {
    super(message);
  }
}
