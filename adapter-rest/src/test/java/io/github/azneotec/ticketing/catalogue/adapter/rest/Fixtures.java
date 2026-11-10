package io.github.azneotec.ticketing.catalogue.adapter.rest;

import io.github.azneotec.ticketing.catalogue.domain.event.Event;
import io.github.azneotec.ticketing.catalogue.domain.event.EventId;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Row;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Seat;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.SeatMap;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Section;
import io.github.azneotec.ticketing.catalogue.domain.venue.Venue;
import io.github.azneotec.ticketing.catalogue.domain.venue.VenueId;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

final class Fixtures {

  static final ZoneId LONDON = ZoneId.of("Europe/London");
  static final Instant STARTS_AT = Instant.parse("2026-12-01T19:30:00Z");
  static final Instant PUBLISHED_AT = Instant.parse("2026-11-11T09:00:00Z");
  static final VenueId VENUE_ID =
      new VenueId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
  static final EventId EVENT_ID =
      new EventId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
  static final SeatMap SEAT_MAP =
      new SeatMap(
          List.of(
              new Section(
                  "Stalls",
                  List.of(
                      new Row("A", List.of(new Seat("1"), new Seat("2"))),
                      new Row("B", List.of(new Seat("1")))))));

  static final String SEAT_MAP_JSON =
      """
      {"sections":[{"name":"Stalls","rows":[
        {"label":"A","seats":["1","2"]},{"label":"B","seats":["1"]}]}]}\
      """;

  private Fixtures() {}

  static Venue venue() {
    return new Venue(VENUE_ID, "Hall", "London", LONDON);
  }

  static Event event(EventStatus status, long version) {
    return Event.restore(
        EVENT_ID, "Concert", VENUE_ID, STARTS_AT, LONDON, SEAT_MAP, status, version);
  }
}
