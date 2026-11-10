package io.github.azneotec.ticketing.catalogue.domain.venue;

import io.github.azneotec.ticketing.catalogue.domain.Guard;
import java.time.ZoneId;

/**
 * A place where events happen. An entity: two venues are the same venue when their ids match,
 * whatever their other values (so this is a class with id-based equality, not a record).
 */
public final class Venue {

  public static final int MAX_NAME_LENGTH = 200;
  public static final int MAX_CITY_LENGTH = 100;

  private final VenueId id;
  private final String name;
  private final String city;
  private final ZoneId zone;

  /**
   * @param zone IANA time zone of the venue (e.g. {@code Europe/London}); events copy it so their
   *     local start time can always be rebuilt from the UTC instant.
   */
  public Venue(VenueId id, String name, String city, ZoneId zone) {
    this.id = Guard.notNull(id, "venue id");
    this.name = Guard.notBlank(name, "venue name", MAX_NAME_LENGTH);
    this.city = Guard.notBlank(city, "venue city", MAX_CITY_LENGTH);
    this.zone = Guard.notNull(zone, "venue zone");
  }

  public VenueId id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String city() {
    return city;
  }

  public ZoneId zone() {
    return zone;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Venue venue && id.equals(venue.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "Venue[" + id + ", " + name + ", " + city + "]";
  }
}
