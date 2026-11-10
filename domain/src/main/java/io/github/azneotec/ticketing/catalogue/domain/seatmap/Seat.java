package io.github.azneotec.ticketing.catalogue.domain.seatmap;

import io.github.azneotec.ticketing.catalogue.domain.Guard;

/** One seat in a row, e.g. {@code 12}. A value object. */
public record Seat(String number) {

  public static final int MAX_NUMBER_LENGTH = 10;

  public Seat {
    number = Guard.notBlank(number, "seat number", MAX_NUMBER_LENGTH);
  }
}
