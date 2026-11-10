package io.github.azneotec.ticketing.catalogue.adapter.persistence;

import static io.github.azneotec.ticketing.catalogue.adapter.persistence.TestData.NOW;
import static io.github.azneotec.ticketing.catalogue.adapter.persistence.TestData.draft;
import static io.github.azneotec.ticketing.catalogue.adapter.persistence.TestData.venue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Hibernate's own @Version check, without our adapter's early check: two transactions load the same
 * row, both change it, the second commit finds "update ... where version = 0" matching no row.
 */
class OptimisticLockingTest extends PostgresTestBase {

  @Autowired JpaEventRepository adapter;
  @Autowired JpaVenueRepository venues;
  @Autowired EventJpaRepository entities;
  @Autowired TransactionTemplate transactions;

  @Test
  void secondWriterOfTheSameVersionFails() {
    var hall = venues.save(venue("Hall"));
    UUID id = adapter.save(draft(hall, "Proms", 30)).id().value();

    assertThatThrownBy(() ->
            transactions.executeWithoutResult(outer -> {
              var mine = entities.findById(id).orElseThrow(); // version 0
              // Another transaction (REQUIRES_NEW would be the same) commits first.
              Thread other =
                  Thread.ofVirtual()
                      .start(() ->
                          transactions.executeWithoutResult(inner -> {
                            var theirs = entities.findById(id).orElseThrow();
                            theirs.setTitle("Renamed by someone else");
                          }));
              join(other);
              mine.setStatus(EventStatus.Code.PUBLISHED, NOW, null, null);
              // Dirty checking turns this into UPDATE ... WHERE id = ? AND version = 0.
            }))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);

    var stored = entities.findById(id).orElseThrow();
    assertThat(stored.getTitle()).isEqualTo("Renamed by someone else");
    assertThat(stored.getStatus()).isEqualTo(EventStatus.Code.DRAFT);
    assertThat(stored.getVersion()).isEqualTo(1);
  }

  private static void join(Thread thread) {
    try {
      thread.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
