package io.github.azneotec.ticketing.catalogue.application.port.in;

import io.github.azneotec.ticketing.catalogue.domain.event.Event;
import io.github.azneotec.ticketing.catalogue.domain.event.EventId;
import java.util.Optional;

/** Inbound port (use case): one event with its seat map. */
public interface GetEvent {

  Optional<Event> getEvent(EventId id);
}
