package io.github.azneotec.ticketing.catalogue.application;

import java.io.Serial;

/** The requested aggregate does not exist. */
public final class NotFoundException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public NotFoundException(String what, Object id) {
    super(what + " " + id + " not found");
  }
}
