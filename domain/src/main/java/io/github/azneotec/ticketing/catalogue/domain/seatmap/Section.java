package io.github.azneotec.ticketing.catalogue.domain.seatmap;

import io.github.azneotec.ticketing.catalogue.domain.Guard;
import java.util.List;

/**
 * A section of the venue, e.g. {@code Stalls} or {@code Balcony}. Row labels are unique within it.
 */
public record Section(String name, List<Row> rows) {

  public static final int MAX_NAME_LENGTH = 50;

  public Section {
    name = Guard.notBlank(name, "section name", MAX_NAME_LENGTH);
    rows = Guard.nonEmptyUnique(rows, Row::label, "rows of section " + name);
  }

  public int capacity() {
    return rows.stream().mapToInt(row -> row.seats().size()).sum();
  }
}
