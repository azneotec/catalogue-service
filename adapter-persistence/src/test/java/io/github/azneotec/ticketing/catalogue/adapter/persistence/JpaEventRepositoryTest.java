package io.github.azneotec.ticketing.catalogue.adapter.persistence;

import static io.github.azneotec.ticketing.catalogue.adapter.persistence.TestData.NOW;
import static io.github.azneotec.ticketing.catalogue.adapter.persistence.TestData.draft;
import static io.github.azneotec.ticketing.catalogue.adapter.persistence.TestData.venue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.azneotec.ticketing.catalogue.application.ConcurrentUpdateException;
import io.github.azneotec.ticketing.catalogue.application.PageRequest;
import io.github.azneotec.ticketing.catalogue.domain.event.EventId;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus.Code;
import io.github.azneotec.ticketing.catalogue.domain.venue.Venue;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JpaEventRepositoryTest extends PostgresTestBase {

  @Autowired JpaEventRepository events;
  @Autowired JpaVenueRepository venues;

  private Venue hall;

  @BeforeEach
  void createVenue() {
    hall = venues.save(venue("Royal Albert Hall"));
  }

  @Test
  void venueRoundTrips() {
    assertThat(venues.findById(hall.id()))
        .hasValueSatisfying(found -> {
          assertThat(found.name()).isEqualTo("Royal Albert Hall");
          assertThat(found.zone()).isEqualTo(hall.zone());
        });
    assertThat(venues.findById(venue("Unsaved").id())).isEmpty();
  }

  @Test
  void eventWithSeatMapRoundTrips() {
    var draft = draft(hall, "Proms", 30, "Stalls", "Balcony");

    var saved = events.save(draft);
    var loaded = events.findById(draft.id()).orElseThrow();

    assertThat(saved.version()).isZero();
    assertThat(loaded.title()).isEqualTo("Proms");
    assertThat(loaded.venueId()).isEqualTo(hall.id());
    assertThat(loaded.startsAt()).isEqualTo(draft.startsAt());
    assertThat(loaded.zone()).isEqualTo(hall.zone());
    assertThat(loaded.status()).isEqualTo(new EventStatus.Draft());
    assertThat(loaded.seatMap()).isEqualTo(draft.seatMap()); // records compare by value
    assertThat(loaded.seatMap().capacity()).isEqualTo(6);
    assertThat(events.findById(EventId.newId())).isEmpty();
  }

  @Test
  void seatMapIsStoredAsQueryableJsonb() {
    var draft = events.save(draft(hall, "Proms", 30, "Stalls"));

    String type =
        jdbc.sql("select jsonb_typeof(seat_map -> 'sections') from event where id = ?")
            .param(draft.id().value())
            .query(String.class)
            .single();
    String firstSeat =
        jdbc.sql("select seat_map #>> '{sections,0,rows,0,seats,0}' from event where id = ?")
            .param(draft.id().value())
            .query(String.class)
            .single();

    assertThat(type).isEqualTo("array");
    assertThat(firstSeat).isEqualTo("1");
  }

  @Test
  void statusChangesAreStoredAndBumpTheVersion() {
    var event = events.save(draft(hall, "Proms", 30));
    event.publish(NOW);
    var published = events.save(event);

    published.cancel(NOW.plusSeconds(60), "Storm");
    var cancelled = events.save(published);

    assertThat(published.version()).isEqualTo(1);
    assertThat(cancelled.version()).isEqualTo(2);
    assertThat(events.findById(event.id()).orElseThrow().status())
        .isEqualTo(new EventStatus.Cancelled(NOW.plusSeconds(60), "Storm"));
  }

  @Test
  void findsSummariesByStatusSoonestFirstWithPaging() {
    var other = venues.save(venue("O2 Arena"));
    var later = draft(hall, "Later", 20);
    var sooner = draft(other, "Sooner", 10);
    later.publish(NOW);
    sooner.publish(NOW);
    events.save(later);
    events.save(sooner);
    events.save(draft(hall, "Still a draft", 5));

    var page = events.findSummaries(Set.of(Code.PUBLISHED), new PageRequest(0, 1));

    assertThat(page.totalItems()).isEqualTo(2);
    assertThat(page.items())
        .singleElement()
        .satisfies(s -> {
          assertThat(s.title()).isEqualTo("Sooner");
          assertThat(s.venueName()).isEqualTo("O2 Arena");
          assertThat(s.venueCity()).isEqualTo("London");
          assertThat(s.zone()).isEqualTo(hall.zone());
          assertThat(s.status()).isEqualTo(Code.PUBLISHED);
        });
    assertThat(
            events
                .findSummaries(Set.of(Code.PUBLISHED), new PageRequest(1, 1))
                .items()
                .getFirst()
                .title())
        .isEqualTo("Later");
    assertThat(events.findSummaries(EnumSet.allOf(Code.class), new PageRequest(0, 10)).items())
        .extracting(s -> s.title())
        .containsExactly("Still a draft", "Sooner", "Later");
  }

  @Test
  void jsonbContainmentFindsEventsBySectionName() {
    var withBalcony = events.save(draft(hall, "Opera", 10, "Stalls", "Balcony"));
    events.save(draft(hall, "Rock", 20, "Standing"));
    var alsoBalcony = events.save(draft(hall, "Ballet", 30, "Balcony"));

    assertThat(events.findIdsWithSection("Balcony"))
        .containsExactly(withBalcony.id(), alsoBalcony.id());
    assertThat(events.findIdsWithSection("Gallery")).isEmpty();
  }

  @Test
  void staleDomainCopyIsRejectedBeforeAnySqlUpdate() {
    var event = events.save(draft(hall, "Proms", 30));
    var tabA = events.findById(event.id()).orElseThrow();
    var tabB = events.findById(event.id()).orElseThrow();

    tabA.publish(NOW);
    events.save(tabA); // version 0 -> 1

    tabB.cancel(NOW, "Cancelled from an old browser tab"); // still believes version 0
    assertThatThrownBy(() -> events.save(tabB))
        .isInstanceOf(ConcurrentUpdateException.class)
        .hasMessageContaining(event.id().toString());
    assertThat(events.findById(event.id()).orElseThrow().status())
        .isInstanceOf(EventStatus.Published.class);
  }
}
