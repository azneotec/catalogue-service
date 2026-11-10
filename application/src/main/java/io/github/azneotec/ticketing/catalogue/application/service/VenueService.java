package io.github.azneotec.ticketing.catalogue.application.service;

import io.github.azneotec.ticketing.catalogue.application.port.in.CreateVenue;
import io.github.azneotec.ticketing.catalogue.application.port.in.GetVenue;
import io.github.azneotec.ticketing.catalogue.application.port.out.VenueRepository;
import io.github.azneotec.ticketing.catalogue.domain.venue.Venue;
import io.github.azneotec.ticketing.catalogue.domain.venue.VenueId;
import java.util.Optional;

/** Venue use cases. Plain Java: wired into Spring by the bootstrap module, not annotated here. */
public final class VenueService implements CreateVenue, GetVenue {

  private final VenueRepository venues;

  public VenueService(VenueRepository venues) {
    this.venues = venues;
  }

  @Override
  public Venue createVenue(Command command) {
    return venues.save(new Venue(VenueId.newId(), command.name(), command.city(), command.zone()));
  }

  @Override
  public Optional<Venue> getVenue(VenueId id) {
    return venues.findById(id);
  }
}
