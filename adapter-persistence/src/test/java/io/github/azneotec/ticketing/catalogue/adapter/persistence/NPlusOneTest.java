package io.github.azneotec.ticketing.catalogue.adapter.persistence;

import static io.github.azneotec.ticketing.catalogue.adapter.persistence.TestData.NOW;
import static io.github.azneotec.ticketing.catalogue.adapter.persistence.TestData.draft;
import static io.github.azneotec.ticketing.catalogue.adapter.persistence.TestData.venue;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.azneotec.ticketing.catalogue.application.PageRequest;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus.Code;
import jakarta.persistence.EntityManagerFactory;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** Counts SQL statements to show the N+1 problem and that ListEvents avoids it. */
class NPlusOneTest extends PostgresTestBase {

  private static final int EVENTS = 5;

  @Autowired JpaEventRepository adapter;
  @Autowired JpaVenueRepository venues;
  @Autowired EventJpaRepository entities;
  @Autowired TransactionTemplate transactions;
  @Autowired EntityManagerFactory entityManagerFactory;

  private Statistics statistics;

  @BeforeEach
  void fiveEventsAtFiveVenues() {
    for (int i = 0; i < EVENTS; i++) {
      var event = draft(venues.save(venue("Venue " + i)), "Event " + i, 10 + i);
      event.publish(NOW);
      adapter.save(event);
    }
    statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
  }

  @Test
  void naiveEntityLoopIssuesOneQueryPerVenue() {
    transactions.executeWithoutResult(
        tx -> entities.findAll().forEach(e -> e.getVenue().getName())); // lazy proxy -> SELECT

    // 1 query for the events + 1 per venue = N+1.
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1 + EVENTS);
  }

  @Test
  void listEventsUsesOneQueryPlusTheCount() {
    var page = adapter.findSummaries(Set.of(Code.PUBLISHED), new PageRequest(0, 3));

    assertThat(page.items()).hasSize(3).allSatisfy(s -> assertThat(s.venueName()).isNotBlank());
    // 1 joined select for the page + 1 count(*) for the total, whatever the page size.
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
  }
}
