package io.github.azneotec.ticketing.catalogue.adapter.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.azneotec.ticketing.catalogue.domain.event.Event;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus.Cancelled;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus.Draft;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus.Published;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * One event with its seat map. The sealed domain status becomes a flat {@code status} code plus the
 * timestamps that exist in that state; fields that don't apply are left out of the JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventDto(
    UUID id,
    String title,
    UUID venueId,
    Instant startsAt,
    ZoneId zone,
    LocalDateTime localStartTime,
    String status,
    Instant publishedAt,
    Instant cancelledAt,
    String cancelReason,
    int capacity,
    SeatMapDto seatMap) {

  static EventDto from(Event event) {
    // Record patterns take each state apart; the switch is exhaustive over the sealed status.
    return switch (event.status()) {
      case Draft() -> of(event, null, null, null);
      case Published(var publishedAt) -> of(event, publishedAt, null, null);
      case Cancelled(var cancelledAt, var reason) -> of(event, null, cancelledAt, reason);
    };
  }

  private static EventDto of(
      Event event, Instant publishedAt, Instant cancelledAt, String cancelReason) {
    return new EventDto(
        event.id().value(),
        event.title(),
        event.venueId().value(),
        event.startsAt(),
        event.zone(),
        event.localStartTime().toLocalDateTime(),
        event.status().code().name(),
        publishedAt,
        cancelledAt,
        cancelReason,
        event.seatMap().capacity(),
        SeatMapDto.from(event.seatMap()));
  }
}
