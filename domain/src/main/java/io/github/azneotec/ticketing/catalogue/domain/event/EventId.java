package io.github.azneotec.ticketing.catalogue.domain.event;

import io.github.azneotec.ticketing.catalogue.domain.Guard;
import java.util.UUID;

/** Typed id of an {@link Event}. */
public record EventId(UUID value) {

  public EventId {
    Guard.notNull(value, "event id");
  }

  public static EventId newId() {
    return new EventId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
