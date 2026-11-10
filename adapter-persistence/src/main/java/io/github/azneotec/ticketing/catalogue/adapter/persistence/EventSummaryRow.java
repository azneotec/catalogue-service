package io.github.azneotec.ticketing.catalogue.adapter.persistence;

import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus;
import java.time.Instant;
import java.util.UUID;

/** One row of the list query, filled by a JPQL constructor expression (not a managed entity). */
record EventSummaryRow(
    UUID id,
    String title,
    Instant startsAt,
    String zoneId,
    EventStatus.Code status,
    String venueName,
    String venueCity) {}
