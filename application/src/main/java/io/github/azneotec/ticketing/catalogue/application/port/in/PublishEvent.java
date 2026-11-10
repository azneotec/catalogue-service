package io.github.azneotec.ticketing.catalogue.application.port.in;

import io.github.azneotec.ticketing.catalogue.application.ConcurrentUpdateException;
import io.github.azneotec.ticketing.catalogue.application.NotFoundException;
import io.github.azneotec.ticketing.catalogue.domain.event.Event;
import io.github.azneotec.ticketing.catalogue.domain.event.EventId;

/** Inbound port (use case): put a draft event on sale. */
public interface PublishEvent {

  /**
   * @param expectedVersion the version the caller last saw (e.g. from an HTTP {@code If-Match});
   *     publishing a version the caller has not seen would act on data they never looked at
   * @throws NotFoundException if the event does not exist
   * @throws ConcurrentUpdateException if the stored version is not {@code expectedVersion}, or the
   *     event changed while we were publishing it
   */
  Event publishEvent(EventId id, long expectedVersion);
}
