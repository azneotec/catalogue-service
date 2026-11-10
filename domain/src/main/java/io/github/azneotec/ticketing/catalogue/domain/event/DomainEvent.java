package io.github.azneotec.ticketing.catalogue.domain.event;

import java.time.Instant;

/**
 * Something that happened to an {@link Event} that other parts of the system may care about. The
 * aggregate collects them; from Phase 2 an outbox stores them in the same transaction as the
 * aggregate and relays them to Kafka (ADR-012).
 */
public sealed interface DomainEvent permits EventPublished, EventCancelled {

  EventId eventId();

  Instant occurredAt();
}
