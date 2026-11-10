package io.github.azneotec.ticketing.catalogue.domain.event;

import io.github.azneotec.ticketing.catalogue.domain.Guard;
import java.time.Instant;

/** An event went on sale. */
public record EventPublished(EventId eventId, Instant occurredAt) implements DomainEvent {

  public EventPublished {
    Guard.notNull(eventId, "eventId");
    Guard.notNull(occurredAt, "occurredAt");
  }
}
