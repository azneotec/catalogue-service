package io.github.azneotec.ticketing.catalogue.application.service;

import io.github.azneotec.ticketing.catalogue.application.port.out.VenueRepository;
import io.github.azneotec.ticketing.catalogue.domain.venue.Venue;
import io.github.azneotec.ticketing.catalogue.domain.venue.VenueId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Test fake of the outbound port: a map instead of Postgres. */
final class InMemoryVenueRepository implements VenueRepository {

  private final Map<VenueId, Venue> venues = new HashMap<>();

  @Override
  public Venue save(Venue venue) {
    venues.put(venue.id(), venue);
    return venue;
  }

  @Override
  public Optional<Venue> findById(VenueId id) {
    return Optional.ofNullable(venues.get(id));
  }
}
