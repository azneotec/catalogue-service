package io.github.azneotec.ticketing.catalogue.application.port.in;

import io.github.azneotec.ticketing.catalogue.application.EventSummary;
import io.github.azneotec.ticketing.catalogue.application.Page;
import io.github.azneotec.ticketing.catalogue.application.PageRequest;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus;
import java.util.Set;

/** Inbound port (use case): page through events, soonest first. */
public interface ListEvents {

  Page<EventSummary> listEvents(Query query);

  /** At least one status; buyers see only {@code PUBLISHED}, the admin API may ask for all. */
  record Query(Set<EventStatus.Code> statuses, PageRequest page) {
    public Query {
      if (statuses == null || statuses.isEmpty()) {
        throw new IllegalArgumentException("statuses must not be empty");
      }
      if (page == null) {
        throw new IllegalArgumentException("page must not be null");
      }
      statuses = Set.copyOf(statuses);
    }
  }
}
