package io.github.azneotec.ticketing.catalogue.domain.seatmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.azneotec.ticketing.catalogue.domain.InvalidValueException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeatMapTest {

  private static Row row(String label, String... seats) {
    return new Row(label, java.util.Arrays.stream(seats).map(Seat::new).toList());
  }

  @Test
  void countsCapacityAndFindsSections() {
    var stalls = new Section("Stalls", List.of(row("A", "1", "2", "3"), row("B", "1", "2")));
    var balcony = new Section("Balcony", List.of(row("A", "1")));
    var map = new SeatMap(List.of(stalls, balcony));

    assertThat(stalls.capacity()).isEqualTo(5);
    assertThat(map.capacity()).isEqualTo(6);
    assertThat(map.section("Balcony")).contains(balcony);
    assertThat(map.section("Gallery")).isEmpty();
  }

  @Test
  void isAValueObjectEqualByContent() {
    assertThat(new SeatMap(List.of(new Section("S", List.of(row("A", "1"))))))
        .isEqualTo(new SeatMap(List.of(new Section("S", List.of(row("A", "1"))))));
    assertThat(new Seat(" 7 ")).isEqualTo(new Seat("7"));
  }

  @Test
  void isImmutableEvenIfTheCallerKeepsChangingItsList() {
    var seats = new ArrayList<>(List.of(new Seat("1")));
    var row = new Row("A", seats);
    seats.add(new Seat("2"));
    assertThat(row.seats()).hasSize(1);
    assertThatThrownBy(() -> row.seats().add(new Seat("3")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsDuplicatesAtEveryLevel() {
    assertThatThrownBy(() -> row("A", "1", "1"))
        .isInstanceOf(InvalidValueException.class)
        .hasMessage("duplicate seats of row A entry '1'");
    assertThatThrownBy(() -> new Section("S", List.of(row("A", "1"), row("A", "2"))))
        .hasMessage("duplicate rows of section S entry 'A'");
    var section = new Section("S", List.of(row("A", "1")));
    assertThatThrownBy(() -> new SeatMap(List.of(section, section)))
        .hasMessage("duplicate sections entry 'S'");
  }

  @Test
  void rejectsEmptyAndBlankParts() {
    assertThatThrownBy(() -> new SeatMap(List.of())).hasMessage("sections must not be empty");
    assertThatThrownBy(() -> new Section("S", List.of()))
        .hasMessage("rows of section S must not be empty");
    assertThatThrownBy(() -> new Row("A", List.of()))
        .hasMessage("seats of row A must not be empty");
    assertThatThrownBy(() -> new Seat(" ")).hasMessage("seat number must not be blank");
    assertThatThrownBy(() -> new Row("ROW-LABEL-TOO-LONG", List.of(new Seat("1"))))
        .hasMessage("row label must be at most 10 characters");
    assertThatThrownBy(() -> new Section("", List.of(row("A", "1"))))
        .hasMessage("section name must not be blank");
  }
}
