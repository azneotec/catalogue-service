package io.github.azneotec.ticketing.catalogue.adapter.rest;

import io.github.azneotec.ticketing.catalogue.domain.seatmap.Row;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Seat;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.SeatMap;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Section;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Seat map as JSON: sections, then rows, then seat numbers. Used in requests and responses. Bean
 * Validation checks only the shape (fields present, lists not empty); the domain checks the rules
 * (lengths, unique labels), so they live in one place.
 */
public record SeatMapDto(@NotEmpty List<@Valid @NotNull SectionDto> sections) {

  public record SectionDto(@NotBlank String name, @NotEmpty List<@Valid @NotNull RowDto> rows) {}

  public record RowDto(@NotBlank String label, @NotEmpty List<@NotBlank String> seats) {}

  static SeatMapDto from(SeatMap seatMap) {
    return new SeatMapDto(
        seatMap.sections().stream()
            .map(section ->
                new SectionDto(
                    section.name(),
                    section.rows().stream()
                        .map(row ->
                            new RowDto(
                                row.label(), row.seats().stream().map(Seat::number).toList()))
                        .toList()))
            .toList());
  }

  SeatMap toDomain() {
    return new SeatMap(
        sections.stream()
            .map(section ->
                new Section(
                    section.name(),
                    section.rows().stream()
                        .map(row ->
                            new Row(row.label(), row.seats().stream().map(Seat::new).toList()))
                        .toList()))
            .toList());
  }
}
