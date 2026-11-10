package io.github.azneotec.ticketing.catalogue.domain.event;

import io.github.azneotec.ticketing.catalogue.domain.Guard;
import java.time.Instant;

/** A published event was cancelled; bookings for it will need refunds (Phase 4). */
public record EventCancelled(EventId eventId, Instant occurredAt, String reason)
    implements DomainEvent {

  public EventCancelled {
    Guard.notNull(eventId, "eventId");
    Guard.notNull(occurredAt, "occurredAt");
    Guard.notNull(reason, "reason");
  }
}
