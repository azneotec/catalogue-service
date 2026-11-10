package io.github.azneotec.ticketing.catalogue.adapter.rest;

import io.github.azneotec.ticketing.catalogue.application.Page;
import java.util.List;
import java.util.function.Function;

/** A page of results as JSON, with enough to draw "page 2 of 7". {@code page} counts from 0. */
public record PageDto<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

  static <S, T> PageDto<T> from(Page<S> page, Function<S, T> mapper) {
    return new PageDto<>(
        page.items().stream().map(mapper).toList(),
        page.request().page(),
        page.request().size(),
        page.totalItems(),
        page.totalPages());
  }
}
