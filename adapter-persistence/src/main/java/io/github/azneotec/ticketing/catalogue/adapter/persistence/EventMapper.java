package io.github.azneotec.ticketing.catalogue.adapter.persistence;

import io.github.azneotec.ticketing.catalogue.adapter.persistence.SeatMapDocument.RowDocument;
import io.github.azneotec.ticketing.catalogue.adapter.persistence.SeatMapDocument.SectionDocument;
import io.github.azneotec.ticketing.catalogue.application.EventSummary;
import io.github.azneotec.ticketing.catalogue.domain.event.Event;
import io.github.azneotec.ticketing.catalogue.domain.event.EventId;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus.Cancelled;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus.Draft;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus.Published;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Row;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Seat;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.SeatMap;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Section;
import io.github.azneotec.ticketing.catalogue.domain.venue.VenueId;
import java.time.ZoneId;

/**
 * Explicit mapping between the domain Event and the JPA EventEntity: the price of keeping them
 * apart.
 */
final class EventMapper {

  private EventMapper() {}

  /** Copies every mutable domain field onto the entity (new or managed). */
  static void copyToEntity(Event event, EventEntity entity) {
    entity.setTitle(event.title());
    entity.setStartsAt(event.startsAt());
    entity.setZoneId(event.zone().getId());
    entity.setSeatMap(toDocument(event.seatMap()));
    // Record patterns take the state apart; the switch is exhaustive because EventStatus is sealed.
    switch (event.status()) {
      case Draft _ -> entity.setStatus(EventStatus.Code.DRAFT, null, null, null);
      case Published(var publishedAt) ->
          entity.setStatus(EventStatus.Code.PUBLISHED, publishedAt, null, null);
      case Cancelled(var cancelledAt, var reason) ->
          entity.setStatus(EventStatus.Code.CANCELLED, null, cancelledAt, reason);
    }
  }

  static Event toDomain(EventEntity entity) {
    EventStatus status =
        switch (entity.getStatus()) {
          case DRAFT -> new Draft();
          case PUBLISHED -> new Published(entity.getPublishedAt());
          case CANCELLED -> new Cancelled(entity.getCancelledAt(), entity.getCancelReason());
        };
    return Event.restore(
        new EventId(entity.getId()),
        entity.getTitle(),
        // getId() on a lazy proxy does not hit the database: Hibernate already knows the id.
        new VenueId(entity.getVenue().getId()),
        entity.getStartsAt(),
        ZoneId.of(entity.getZoneId()),
        toSeatMap(entity.getSeatMap()),
        status,
        entity.getVersion());
  }

  static EventSummary toSummary(EventSummaryRow row) {
    return new EventSummary(
        new EventId(row.id()),
        row.title(),
        row.startsAt(),
        ZoneId.of(row.zoneId()),
        row.status(),
        row.venueName(),
        row.venueCity());
  }

  static SeatMapDocument toDocument(SeatMap seatMap) {
    return new SeatMapDocument(
        seatMap.sections().stream()
            .map(section ->
                new SectionDocument(
                    section.name(),
                    section.rows().stream()
                        .map(row ->
                            new RowDocument(
                                row.label(), row.seats().stream().map(Seat::number).toList()))
                        .toList()))
            .toList());
  }

  /** Runs every domain validation again: data in the database is not trusted blindly. */
  static SeatMap toSeatMap(SeatMapDocument document) {
    return new SeatMap(
        document.sections().stream()
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
