package io.github.azneotec.ticketing.catalogue.application.port.in;

import io.github.azneotec.ticketing.catalogue.application.NotFoundException;
import io.github.azneotec.ticketing.catalogue.domain.event.Event;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.SeatMap;
import io.github.azneotec.ticketing.catalogue.domain.venue.VenueId;
import java.time.Instant;

/** Inbound port (use case): create a draft event at an existing venue. */
public interface CreateEvent {

  /**
   * @throws NotFoundException if the venue does not exist
   */
  Event createEvent(Command command);

  record Command(String title, VenueId venueId, Instant startsAt, SeatMap seatMap) {}
}
