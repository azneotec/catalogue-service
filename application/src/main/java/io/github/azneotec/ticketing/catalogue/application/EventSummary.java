package io.github.azneotec.ticketing.catalogue.application;

import io.github.azneotec.ticketing.catalogue.domain.event.EventId;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Read model for event lists: what a listing shows, joined with the venue, without the seat map. A
 * query result, not an aggregate, so it can be read in one SQL query (no N+1, no JSONB).
 */
public record EventSummary(
    EventId id,
    String title,
    Instant startsAt,
    ZoneId zone,
    EventStatus.Code status,
    String venueName,
    String venueCity) {}
