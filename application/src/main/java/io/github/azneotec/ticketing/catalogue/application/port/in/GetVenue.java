package io.github.azneotec.ticketing.catalogue.application.port.in;

import io.github.azneotec.ticketing.catalogue.domain.venue.Venue;
import io.github.azneotec.ticketing.catalogue.domain.venue.VenueId;
import java.util.Optional;

/** Inbound port (use case): one venue, e.g. to follow the {@code Location} of a new venue. */
public interface GetVenue {

  Optional<Venue> getVenue(VenueId id);
}
