package io.github.azneotec.ticketing.catalogue.domain.event;

import io.github.azneotec.ticketing.catalogue.domain.Guard;
import io.github.azneotec.ticketing.catalogue.domain.IllegalTransitionException;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus.Cancelled;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus.Draft;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus.Published;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.SeatMap;
import io.github.azneotec.ticketing.catalogue.domain.venue.Venue;
import io.github.azneotec.ticketing.catalogue.domain.venue.VenueId;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate root: an event (concert, match, show) at a venue, with its seat map and lifecycle. All
 * changes go through its methods, which enforce the rules and record {@link DomainEvent}s.
 *
 * <p>It refers to its venue by id only: aggregates reference each other by identity, so loading an
 * event never drags in another aggregate.
 */
public final class Event {

  public static final int MAX_TITLE_LENGTH = 200;

  private final EventId id;
  private final String title;
  private final VenueId venueId;
  private final Instant startsAt;
  private final ZoneId zone;
  private final SeatMap seatMap;
  private final long version;
  private EventStatus status;
  private final List<DomainEvent> domainEvents = new ArrayList<>();

  private Event(
      EventId id,
      String title,
      VenueId venueId,
      Instant startsAt,
      ZoneId zone,
      SeatMap seatMap,
      EventStatus status,
      long version) {
    this.id = Guard.notNull(id, "event id");
    this.title = Guard.notBlank(title, "event title", MAX_TITLE_LENGTH);
    this.venueId = Guard.notNull(venueId, "venue id");
    this.startsAt = Guard.notNull(startsAt, "startsAt");
    this.zone = Guard.notNull(zone, "zone");
    this.seatMap = Guard.notNull(seatMap, "seat map");
    this.status = Guard.notNull(status, "status");
    this.version = version;
  }

  /** A new event starts as a {@link Draft} and takes its time zone from the venue. */
  public static Event create(
      EventId id, String title, Venue venue, Instant startsAt, SeatMap seatMap) {
    Guard.notNull(venue, "venue");
    return new Event(id, title, venue.id(), startsAt, venue.zone(), seatMap, new Draft(), 0L);
  }

  /**
   * Rebuilds an event from storage. No rules are re-run and no domain events are recorded: this is
   * a state that already happened.
   *
   * @param version concurrency token from storage; opaque to the domain, handed back on save so a
   *     concurrent change can be detected (optimistic locking)
   */
  public static Event restore(
      EventId id,
      String title,
      VenueId venueId,
      Instant startsAt,
      ZoneId zone,
      SeatMap seatMap,
      EventStatus status,
      long version) {
    return new Event(id, title, venueId, startsAt, zone, seatMap, status, version);
  }

  /** Puts the event on sale. Only a draft that starts in the future can be published. */
  public void publish(Instant now) {
    Guard.notNull(now, "now");
    status =
        switch (status) {
          case Draft _ -> {
            if (!startsAt.isAfter(now)) {
              throw new IllegalTransitionException(
                  "event " + id + " cannot be published: it starts in the past");
            }
            domainEvents.add(new EventPublished(id, now));
            yield new Published(now);
          }
          case Published _ ->
              throw new IllegalTransitionException("event " + id + " is already published");
          case Cancelled _ ->
              throw new IllegalTransitionException(
                  "event " + id + " is cancelled and cannot be published");
        };
  }

  /**
   * Cancels the event. Cancelling a published event records {@link EventCancelled} (buyers may hold
   * tickets); cancelling a draft does not, because nobody outside the organiser has seen it.
   */
  public void cancel(Instant now, String reason) {
    var cancelled = new Cancelled(now, reason); // validates before any state changes
    status =
        switch (status) {
          case Draft _ -> cancelled;
          case Published _ -> {
            domainEvents.add(new EventCancelled(id, now, cancelled.reason()));
            yield cancelled;
          }
          case Cancelled _ ->
              throw new IllegalTransitionException("event " + id + " is already cancelled");
        };
  }

  /** Returns the domain events recorded since the last call, and forgets them. */
  public List<DomainEvent> pullDomainEvents() {
    var pulled = List.copyOf(domainEvents);
    domainEvents.clear();
    return pulled;
  }

  /**
   * Start time on the venue's wall clock, e.g. 19:30 in London, whatever zone the server runs in.
   */
  public ZonedDateTime localStartTime() {
    return startsAt.atZone(zone);
  }

  public EventId id() {
    return id;
  }

  public String title() {
    return title;
  }

  public VenueId venueId() {
    return venueId;
  }

  public Instant startsAt() {
    return startsAt;
  }

  public ZoneId zone() {
    return zone;
  }

  public SeatMap seatMap() {
    return seatMap;
  }

  public EventStatus status() {
    return status;
  }

  public long version() {
    return version;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Event event && id.equals(event.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "Event[" + id + ", " + title + ", " + status.code() + "]";
  }
}
