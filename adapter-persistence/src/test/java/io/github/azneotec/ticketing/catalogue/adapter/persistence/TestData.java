package io.github.azneotec.ticketing.catalogue.adapter.persistence;

import io.github.azneotec.ticketing.catalogue.domain.event.Event;
import io.github.azneotec.ticketing.catalogue.domain.event.EventId;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Row;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Seat;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.SeatMap;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Section;
import io.github.azneotec.ticketing.catalogue.domain.venue.Venue;
import io.github.azneotec.ticketing.catalogue.domain.venue.VenueId;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

final class TestData {

  static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

  private TestData() {}

  static Venue venue(String name) {
    return new Venue(VenueId.newId(), name, "London", ZoneId.of("Europe/London"));
  }

  static SeatMap seatMap(String... sectionNames) {
    return new SeatMap(
        java.util.Arrays.stream(sectionNames)
            .map(name ->
                new Section(
                    name,
                    List.of(
                        new Row("A", List.of(new Seat("1"), new Seat("2"))),
                        new Row("B", List.of(new Seat("1"))))))
            .toList());
  }

  static Event draft(Venue venue, String title, int daysFromNow, String... sections) {
    return Event.create(
        EventId.newId(),
        title,
        venue,
        NOW.plusSeconds(daysFromNow * 86_400L),
        seatMap(sections.length == 0 ? new String[] {"Stalls"} : sections));
  }
}
