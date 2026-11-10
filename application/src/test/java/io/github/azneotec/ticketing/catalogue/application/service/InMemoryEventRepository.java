package io.github.azneotec.ticketing.catalogue.application.service;

import io.github.azneotec.ticketing.catalogue.application.ConcurrentUpdateException;
import io.github.azneotec.ticketing.catalogue.application.EventSummary;
import io.github.azneotec.ticketing.catalogue.application.Page;
import io.github.azneotec.ticketing.catalogue.application.PageRequest;
import io.github.azneotec.ticketing.catalogue.application.port.out.EventRepository;
import io.github.azneotec.ticketing.catalogue.domain.event.Event;
import io.github.azneotec.ticketing.catalogue.domain.event.EventId;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Test fake of the outbound port. It follows the same contract as the JPA adapter (version check,
 * version bump, a fresh copy on every read), so service tests mean something.
 */
final class InMemoryEventRepository implements EventRepository {

  private final Map<EventId, Event> events = new HashMap<>();
  private final InMemoryVenueRepository venues;

  InMemoryEventRepository(InMemoryVenueRepository venues) {
    this.venues = venues;
  }

  @Override
  public Event save(Event event) {
    var stored = events.get(event.id());
    long nextVersion = 0;
    if (stored != null) {
      if (stored.version() != event.version()) {
        throw new ConcurrentUpdateException("event", event.id(), null);
      }
      nextVersion = stored.version() + 1;
    }
    var saved = copy(event, nextVersion);
    events.put(event.id(), saved);
    return copy(saved, nextVersion);
  }

  @Override
  public Optional<Event> findById(EventId id) {
    return Optional.ofNullable(events.get(id)).map(e -> copy(e, e.version()));
  }

  @Override
  public Page<EventSummary> findSummaries(Set<EventStatus.Code> statuses, PageRequest page) {
    var matching =
        events.values().stream()
            .filter(e -> statuses.contains(e.status().code()))
            .sorted(Comparator.comparing(Event::startsAt))
            .toList();
    var items =
        matching.stream()
            .skip(page.offset())
            .limit(page.size())
            .map(e -> {
              var venue = venues.findById(e.venueId()).orElseThrow();
              return new EventSummary(
                  e.id(),
                  e.title(),
                  e.startsAt(),
                  e.zone(),
                  e.status().code(),
                  venue.name(),
                  venue.city());
            })
            .toList();
    return new Page<>(items, page, matching.size());
  }

  private static Event copy(Event e, long version) {
    return Event.restore(
        e.id(), e.title(), e.venueId(), e.startsAt(), e.zone(), e.seatMap(), e.status(), version);
  }
}
