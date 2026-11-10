package io.github.azneotec.ticketing.catalogue.domain.seatmap;

import io.github.azneotec.ticketing.catalogue.domain.Guard;
import java.util.List;

/** A row of seats, e.g. row {@code A}. Seat numbers are unique within the row. */
public record Row(String label, List<Seat> seats) {

  public static final int MAX_LABEL_LENGTH = 10;

  public Row {
    label = Guard.notBlank(label, "row label", MAX_LABEL_LENGTH);
    seats = Guard.nonEmptyUnique(seats, Seat::number, "seats of row " + label);
  }
}
