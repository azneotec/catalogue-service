package io.github.azneotec.ticketing.catalogue.adapter.persistence;

import java.util.List;

/**
 * Shape of the {@code seat_map} JSONB column, e.g. {@code {"sections":[{"name":"Stalls","rows":
 * [{"label":"A","seats":["1","2"]}]}]}}. Separate from the domain records on purpose: renaming a
 * domain field must not silently change (and break) the JSON already stored in the database.
 */
record SeatMapDocument(List<SectionDocument> sections) {

  record SectionDocument(String name, List<RowDocument> rows) {}

  record RowDocument(String label, List<String> seats) {}
}
