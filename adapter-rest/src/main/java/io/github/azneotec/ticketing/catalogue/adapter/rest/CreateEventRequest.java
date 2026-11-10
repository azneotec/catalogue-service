package io.github.azneotec.ticketing.catalogue.adapter.rest;

import io.github.azneotec.ticketing.catalogue.application.port.in.CreateEvent;
import io.github.azneotec.ticketing.catalogue.domain.venue.VenueId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Body of {@code POST /admin/events}. {@code startsAt} is an instant, e.g. {@code ...T19:30:00Z}.
 */
public record CreateEventRequest(
    @NotBlank String title,
    @NotNull UUID venueId,
    @NotNull Instant startsAt,
    @NotNull @Valid SeatMapDto seatMap) {

  CreateEvent.Command toCommand() {
    return new CreateEvent.Command(title, new VenueId(venueId), startsAt, seatMap.toDomain());
  }
}
