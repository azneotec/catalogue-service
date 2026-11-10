package io.github.azneotec.ticketing.catalogue.domain.seatmap;

import io.github.azneotec.ticketing.catalogue.domain.Guard;
import java.util.List;
import java.util.Optional;

/**
 * The seating layout of one event: sections, then rows, then seats. A tree of value objects,
 * immutable all the way down, so an event's seat map can only change by replacing it whole.
 */
public record SeatMap(List<Section> sections) {

  public SeatMap {
    sections = Guard.nonEmptyUnique(sections, Section::name, "sections");
  }

  public int capacity() {
    return sections.stream().mapToInt(Section::capacity).sum();
  }

  public Optional<Section> section(String name) {
    return sections.stream().filter(section -> section.name().equals(name)).findFirst();
  }
}
