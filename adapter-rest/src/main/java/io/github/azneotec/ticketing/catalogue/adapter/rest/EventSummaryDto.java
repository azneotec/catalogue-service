package io.github.azneotec.ticketing.catalogue.adapter.rest;

import io.github.azneotec.ticketing.catalogue.application.EventSummary;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/** One line of an event list: no seat map, venue name and city joined in. */
public record EventSummaryDto(
    UUID id,
    String title,
    Instant startsAt,
    ZoneId zone,
    LocalDateTime localStartTime,
    String status,
    String venueName,
    String venueCity) {

  static EventSummaryDto from(EventSummary summary) {
    return new EventSummaryDto(
        summary.id().value(),
        summary.title(),
        summary.startsAt(),
        summary.zone(),
        LocalDateTime.ofInstant(summary.startsAt(), summary.zone()),
        summary.status().name(),
        summary.venueName(),
        summary.venueCity());
  }
}
