package io.github.azneotec.ticketing.catalogue.application.port.in;

import io.github.azneotec.ticketing.catalogue.domain.venue.Venue;
import java.time.ZoneId;

/** Inbound port (use case): register a venue. */
public interface CreateVenue {

  Venue createVenue(Command command);

  record Command(String name, String city, ZoneId zone) {}
}
