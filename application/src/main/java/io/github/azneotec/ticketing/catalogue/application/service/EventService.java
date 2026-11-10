package io.github.azneotec.ticketing.catalogue.application.service;

import io.github.azneotec.ticketing.catalogue.application.EventSummary;
import io.github.azneotec.ticketing.catalogue.application.NotFoundException;
import io.github.azneotec.ticketing.catalogue.application.Page;
import io.github.azneotec.ticketing.catalogue.application.port.in.CreateEvent;
import io.github.azneotec.ticketing.catalogue.application.port.in.GetEvent;
import io.github.azneotec.ticketing.catalogue.application.port.in.ListEvents;
import io.github.azneotec.ticketing.catalogue.application.port.in.PublishEvent;
import io.github.azneotec.ticketing.catalogue.application.port.out.EventRepository;
import io.github.azneotec.ticketing.catalogue.application.port.out.VenueRepository;
import io.github.azneotec.ticketing.catalogue.domain.event.Event;
import io.github.azneotec.ticketing.catalogue.domain.event.EventId;
import java.time.Clock;
import java.util.Optional;

/**
 * Event use cases: load through a port, let the aggregate apply its rules, save through a port. The
 * {@link Clock} is injected so tests control "now".
 */
public final class EventService implements CreateEvent, PublishEvent, GetEvent, ListEvents {

  private final EventRepository events;
  private final VenueRepository venues;
  private final Clock clock;

  public EventService(EventRepository events, VenueRepository venues, Clock clock) {
    this.events = events;
    this.venues = venues;
    this.clock = clock;
  }

  @Override
  public Event createEvent(CreateEvent.Command command) {
    var venue =
        venues
            .findById(command.venueId())
            .orElseThrow(() -> new NotFoundException("venue", command.venueId()));
    var event =
        Event.create(
            EventId.newId(), command.title(), venue, command.startsAt(), command.seatMap());
    return events.save(event);
  }

  /**
   * Read and write run in separate short transactions. The version read here travels with the
   * aggregate, so if anyone saves the event in between, our save fails instead of overwriting their
   * change (optimistic locking, see the persistence adapter).
   */
  @Override
  public Event publishEvent(EventId id) {
    var event = events.findById(id).orElseThrow(() -> new NotFoundException("event", id));
    event.publish(clock.instant());
    // Phase 2: an outbox stores event.pullDomainEvents() in the same transaction as the save.
    return events.save(event);
  }

  @Override
  public Optional<Event> getEvent(EventId id) {
    return events.findById(id);
  }

  @Override
  public Page<EventSummary> listEvents(ListEvents.Query query) {
    return events.findSummaries(query.statuses(), query.page());
  }
}
