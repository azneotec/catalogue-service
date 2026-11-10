package io.github.azneotec.ticketing.catalogue.application;

import java.util.List;

/** One page of results plus the total, so a client can draw "page 2 of 7". */
public record Page<T>(List<T> items, PageRequest request, long totalItems) {

  public Page {
    items = List.copyOf(items);
  }

  public int totalPages() {
    return (int) ((totalItems + request.size() - 1) / request.size());
  }
}
