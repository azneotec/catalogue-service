package io.github.azneotec.ticketing.catalogue.domain.venue;

import io.github.azneotec.ticketing.catalogue.domain.Guard;
import java.util.UUID;

/** Typed id: a VenueId can't be passed where an EventId is expected, unlike a bare UUID. */
public record VenueId(UUID value) {

  public VenueId {
    Guard.notNull(value, "venue id");
  }

  public static VenueId newId() {
    return new VenueId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
