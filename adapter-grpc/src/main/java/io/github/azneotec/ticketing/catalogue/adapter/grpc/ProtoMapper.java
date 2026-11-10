package io.github.azneotec.ticketing.catalogue.adapter.grpc;

import com.google.protobuf.Timestamp;
import io.github.azneotec.ticketing.catalogue.application.EventSummary;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus.Code;
import io.github.azneotec.ticketing.catalogue.domain.venue.Venue;
import io.github.azneotec.ticketing.catalogue.grpc.v1.Event;
import io.github.azneotec.ticketing.catalogue.grpc.v1.EventStatus;
import io.github.azneotec.ticketing.catalogue.grpc.v1.Schedule;
import io.github.azneotec.ticketing.catalogue.grpc.v1.VenueSummary;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Domain and read-model types to the generated Protobuf messages. {@code Schedule.ends_at} stays
 * unset: the domain has no end time yet (an unset message field is the Protobuf way of "absent").
 */
final class ProtoMapper {

  private ProtoMapper() {}

  static Event event(io.github.azneotec.ticketing.catalogue.domain.event.Event event, Venue venue) {
    return Event.newBuilder()
        .setEventId(event.id().toString())
        .setTitle(event.title())
        .setVenue(
            VenueSummary.newBuilder()
                .setVenueId(venue.id().toString())
                .setName(venue.name())
                .setCity(venue.city()))
        .setSchedule(schedule(event.startsAt(), event.zone()))
        .setStatus(status(event.status().code()))
        .build();
  }

  static Event summary(EventSummary summary) {
    // The summary has no venue id; other services needing it call GetEvent.
    return Event.newBuilder()
        .setEventId(summary.id().toString())
        .setTitle(summary.title())
        .setVenue(
            VenueSummary.newBuilder().setName(summary.venueName()).setCity(summary.venueCity()))
        .setSchedule(schedule(summary.startsAt(), summary.zone()))
        .setStatus(status(summary.status()))
        .build();
  }

  static EventStatus status(Code code) {
    return switch (code) {
      case DRAFT -> EventStatus.EVENT_STATUS_DRAFT;
      case PUBLISHED -> EventStatus.EVENT_STATUS_PUBLISHED;
      case CANCELLED -> EventStatus.EVENT_STATUS_CANCELLED;
    };
  }

  private static Schedule.Builder schedule(Instant startsAt, ZoneId zone) {
    return Schedule.newBuilder().setStartsAt(timestamp(startsAt)).setZoneId(zone.getId());
  }

  static Timestamp timestamp(Instant instant) {
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }
}
