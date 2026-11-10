package io.github.azneotec.ticketing.catalogue.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.azneotec.ticketing.catalogue.application.ConcurrentUpdateException;
import io.github.azneotec.ticketing.catalogue.application.NotFoundException;
import io.github.azneotec.ticketing.catalogue.application.PageRequest;
import io.github.azneotec.ticketing.catalogue.application.port.in.CreateEvent;
import io.github.azneotec.ticketing.catalogue.application.port.in.CreateVenue;
import io.github.azneotec.ticketing.catalogue.application.port.in.ListEvents;
import io.github.azneotec.ticketing.catalogue.domain.IllegalTransitionException;
import io.github.azneotec.ticketing.catalogue.domain.event.EventId;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus.Code;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Row;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Seat;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.SeatMap;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Section;
import io.github.azneotec.ticketing.catalogue.domain.venue.Venue;
import io.github.azneotec.ticketing.catalogue.domain.venue.VenueId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
  private static final SeatMap SEAT_MAP =
      new SeatMap(List.of(new Section("Stalls", List.of(new Row("A", List.of(new Seat("1")))))));

  private final InMemoryVenueRepository venueRepository = new InMemoryVenueRepository();
  private final InMemoryEventRepository eventRepository =
      new InMemoryEventRepository(venueRepository);
  private final EventService service =
      new EventService(eventRepository, venueRepository, Clock.fixed(NOW, ZoneOffset.UTC));
  private Venue venue;

  @BeforeEach
  void createVenue() {
    venue =
        new VenueService(venueRepository)
            .createVenue(new CreateVenue.Command("Hall", "London", ZoneId.of("Europe/London")));
  }

  private CreateEvent.Command command(String title, Duration fromNow) {
    return new CreateEvent.Command(title, venue.id(), NOW.plus(fromNow), SEAT_MAP);
  }

  @Test
  void createVenueStoresIt() {
    assertThat(venueRepository.findById(venue.id())).contains(venue);
    assertThat(venue.name()).isEqualTo("Hall");
  }

  @Test
  void createEventStoresADraftAtTheVenue() {
    var event = service.createEvent(command("Concert", Duration.ofDays(30)));

    assertThat(event.status()).isEqualTo(new EventStatus.Draft());
    assertThat(event.zone()).isEqualTo(ZoneId.of("Europe/London"));
    assertThat(service.getEvent(event.id())).contains(event);
  }

  @Test
  void createEventFailsForAnUnknownVenue() {
    var unknown = VenueId.newId();
    assertThatThrownBy(() ->
            service.createEvent(
                new CreateEvent.Command("X", unknown, NOW.plusSeconds(60), SEAT_MAP)))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("venue " + unknown + " not found");
  }

  @Test
  void publishEventUsesTheClockAndBumpsTheVersion() {
    var created = service.createEvent(command("Concert", Duration.ofDays(30)));

    var published = service.publishEvent(created.id());

    assertThat(published.status()).isEqualTo(new EventStatus.Published(NOW));
    assertThat(published.version()).isEqualTo(created.version() + 1);
    assertThat(service.getEvent(created.id()).orElseThrow().status())
        .isEqualTo(new EventStatus.Published(NOW));
  }

  @Test
  void publishEventFailsForUnknownOrIllegalEvents() {
    var unknown = EventId.newId();
    assertThatThrownBy(() -> service.publishEvent(unknown)).isInstanceOf(NotFoundException.class);

    var started = service.createEvent(command("Too late", Duration.ofHours(-1)));
    assertThatThrownBy(() -> service.publishEvent(started.id()))
        .isInstanceOf(IllegalTransitionException.class);
  }

  @Test
  void aStaleCopyCannotOverwriteANewerSave() {
    var created = service.createEvent(command("Concert", Duration.ofDays(30)));
    var staleCopy = service.getEvent(created.id()).orElseThrow();
    service.publishEvent(created.id());

    staleCopy.cancel(NOW, "Cancelled from an old screen");
    assertThatThrownBy(() -> eventRepository.save(staleCopy))
        .isInstanceOf(ConcurrentUpdateException.class)
        .hasMessageContaining("changed by someone else");
  }

  @Test
  void listEventsFiltersByStatusAndPagesSoonestFirst() {
    var later = service.createEvent(command("Later", Duration.ofDays(20)));
    var sooner = service.createEvent(command("Sooner", Duration.ofDays(10)));
    service.createEvent(command("Draft", Duration.ofDays(5)));
    service.publishEvent(later.id());
    service.publishEvent(sooner.id());

    var firstPage =
        service.listEvents(new ListEvents.Query(Set.of(Code.PUBLISHED), new PageRequest(0, 1)));

    assertThat(firstPage.totalItems()).isEqualTo(2);
    assertThat(firstPage.totalPages()).isEqualTo(2);
    assertThat(firstPage.items())
        .singleElement()
        .satisfies(summary -> {
          assertThat(summary.title()).isEqualTo("Sooner");
          assertThat(summary.venueName()).isEqualTo("Hall");
          assertThat(summary.venueCity()).isEqualTo("London");
          assertThat(summary.status()).isEqualTo(Code.PUBLISHED);
        });

    var all =
        service.listEvents(new ListEvents.Query(EnumSet.allOf(Code.class), new PageRequest(0, 10)));
    assertThat(all.items()).extracting(s -> s.title()).containsExactly("Draft", "Sooner", "Later");
  }
}
