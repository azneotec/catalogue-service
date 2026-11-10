package io.github.azneotec.ticketing.catalogue.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.azneotec.ticketing.catalogue.domain.IllegalTransitionException;
import io.github.azneotec.ticketing.catalogue.domain.InvalidValueException;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus.Cancelled;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus.Draft;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus.Published;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Row;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Seat;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.SeatMap;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Section;
import io.github.azneotec.ticketing.catalogue.domain.venue.Venue;
import io.github.azneotec.ticketing.catalogue.domain.venue.VenueId;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventTest {

  private static final ZoneId LONDON = ZoneId.of("Europe/London");
  private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
  private static final Instant STARTS_AT = Instant.parse("2027-06-01T18:30:00Z");
  private static final Venue VENUE = new Venue(VenueId.newId(), "Hall", "London", LONDON);
  private static final SeatMap SEAT_MAP =
      new SeatMap(List.of(new Section("Stalls", List.of(new Row("A", List.of(new Seat("1")))))));

  private static Event draft() {
    return Event.create(EventId.newId(), "Concert", VENUE, STARTS_AT, SEAT_MAP);
  }

  @Test
  void createStartsAsDraftAtTheVenuesZone() {
    var id = EventId.newId();
    var event = Event.create(id, " Concert ", VENUE, STARTS_AT, SEAT_MAP);

    assertThat(event.id()).isEqualTo(id);
    assertThat(event.title()).isEqualTo("Concert");
    assertThat(event.venueId()).isEqualTo(VENUE.id());
    assertThat(event.zone()).isEqualTo(LONDON);
    assertThat(event.startsAt()).isEqualTo(STARTS_AT);
    assertThat(event.seatMap()).isEqualTo(SEAT_MAP);
    assertThat(event.status()).isEqualTo(new Draft());
    assertThat(event.version()).isZero();
    assertThat(event.pullDomainEvents()).isEmpty();
    assertThat(event).hasToString("Event[" + id + ", Concert, DRAFT]");
  }

  @Test
  void localStartTimeUsesTheVenueWallClock() {
    // 18:30 UTC on 1 June is 19:30 in London (British Summer Time).
    assertThat(draft().localStartTime().toLocalDateTime())
        .isEqualTo(LocalDateTime.parse("2027-06-01T19:30"));
  }

  @Test
  void publishingADraftRecordsEventPublished() {
    var event = draft();
    event.publish(NOW);

    assertThat(event.status()).isEqualTo(new Published(NOW));
    assertThat(event.pullDomainEvents()).containsExactly(new EventPublished(event.id(), NOW));
    assertThat(event.pullDomainEvents()).as("pulling clears them").isEmpty();
  }

  @Test
  void cannotPublishAnEventThatAlreadyStarted() {
    var event = draft();
    assertThatThrownBy(() -> event.publish(STARTS_AT))
        .isInstanceOf(IllegalTransitionException.class)
        .hasMessageContaining("starts in the past");
    assertThat(event.status()).isEqualTo(new Draft());
    assertThat(event.pullDomainEvents()).isEmpty();
  }

  @Test
  void cannotPublishTwiceOrAfterCancel() {
    var published = draft();
    published.publish(NOW);
    assertThatThrownBy(() -> published.publish(NOW)).hasMessageContaining("already published");

    var cancelled = draft();
    cancelled.cancel(NOW, "Venue flooded");
    assertThatThrownBy(() -> cancelled.publish(NOW))
        .isInstanceOf(IllegalTransitionException.class)
        .hasMessageContaining("is cancelled");
  }

  @Test
  void cancellingADraftRecordsNoDomainEvent() {
    var event = draft();
    event.cancel(NOW, "Changed plans");
    assertThat(event.status()).isEqualTo(new Cancelled(NOW, "Changed plans"));
    assertThat(event.pullDomainEvents()).isEmpty();
  }

  @Test
  void cancellingAPublishedEventRecordsEventCancelled() {
    var event = draft();
    event.publish(NOW);
    event.pullDomainEvents();
    event.cancel(NOW.plusSeconds(60), " Artist ill ");

    assertThat(event.status().code()).isEqualTo(EventStatus.Code.CANCELLED);
    assertThat(event.pullDomainEvents())
        .containsExactly(new EventCancelled(event.id(), NOW.plusSeconds(60), "Artist ill"));
  }

  @Test
  void cannotCancelTwiceAndAnInvalidReasonChangesNothing() {
    var event = draft();
    assertThatThrownBy(() -> event.cancel(NOW, " ")).isInstanceOf(InvalidValueException.class);
    assertThat(event.status()).isEqualTo(new Draft());

    event.cancel(NOW, "Reason");
    assertThatThrownBy(() -> event.cancel(NOW, "Again")).hasMessageContaining("already cancelled");
  }

  @Test
  void restoreRebuildsStateWithoutRecordingEvents() {
    var id = EventId.newId();
    var event =
        Event.restore(id, "Show", VENUE.id(), STARTS_AT, LONDON, SEAT_MAP, new Published(NOW), 7);
    assertThat(event.status()).isEqualTo(new Published(NOW));
    assertThat(event.version()).isEqualTo(7);
    assertThat(event.pullDomainEvents()).isEmpty();
  }

  @Test
  void isAnEntityEqualById() {
    var id = EventId.newId();
    var a = Event.create(id, "A", VENUE, STARTS_AT, SEAT_MAP);
    var b = Event.restore(id, "B", VENUE.id(), NOW, LONDON, SEAT_MAP, new Draft(), 3);
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(draft()).isNotEqualTo("A");
  }

  @Test
  void rejectsInvalidValues() {
    var id = EventId.newId();
    assertThatThrownBy(() -> Event.create(id, " ", VENUE, STARTS_AT, SEAT_MAP))
        .hasMessage("event title must not be blank");
    assertThatThrownBy(() -> Event.create(id, "T", null, STARTS_AT, SEAT_MAP))
        .hasMessage("venue must not be null");
    assertThatThrownBy(() -> Event.create(id, "T", VENUE, null, SEAT_MAP))
        .hasMessage("startsAt must not be null");
    assertThatThrownBy(() -> Event.create(id, "T", VENUE, STARTS_AT, null))
        .hasMessage("seat map must not be null");
    assertThatThrownBy(() -> draft().publish(null)).hasMessage("now must not be null");
    assertThatThrownBy(() -> new EventId(null)).isInstanceOf(InvalidValueException.class);
    assertThat(new EventId(UUID.fromString("00000000-0000-0000-0000-000000000001")))
        .hasToString("00000000-0000-0000-0000-000000000001");
  }

  @Test
  void statusRecordsValidateTheirData() {
    assertThat(new Draft().code()).isEqualTo(EventStatus.Code.DRAFT);
    assertThat(new Published(NOW).code()).isEqualTo(EventStatus.Code.PUBLISHED);
    assertThatThrownBy(() -> new Published(null)).isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(() -> new Cancelled(null, "r")).isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(() -> new EventPublished(null, NOW))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(() -> new EventCancelled(EventId.newId(), null, "r"))
        .isInstanceOf(InvalidValueException.class);
  }
}
