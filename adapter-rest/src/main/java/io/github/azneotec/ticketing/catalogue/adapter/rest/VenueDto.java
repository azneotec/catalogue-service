package io.github.azneotec.ticketing.catalogue.adapter.rest;

import io.github.azneotec.ticketing.catalogue.domain.venue.Venue;
import java.time.ZoneId;
import java.util.UUID;

/** A venue as JSON. */
public record VenueDto(UUID id, String name, String city, ZoneId zone) {

  static VenueDto from(Venue venue) {
    return new VenueDto(venue.id().value(), venue.name(), venue.city(), venue.zone());
  }
}
