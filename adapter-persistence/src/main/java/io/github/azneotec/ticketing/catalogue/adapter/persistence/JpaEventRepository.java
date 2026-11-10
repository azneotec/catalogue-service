package io.github.azneotec.ticketing.catalogue.adapter.persistence;

import io.github.azneotec.ticketing.catalogue.application.ConcurrentUpdateException;
import io.github.azneotec.ticketing.catalogue.application.EventSummary;
import io.github.azneotec.ticketing.catalogue.application.Page;
import io.github.azneotec.ticketing.catalogue.application.PageRequest;
import io.github.azneotec.ticketing.catalogue.application.port.out.EventRepository;
import io.github.azneotec.ticketing.catalogue.domain.event.Event;
import io.github.azneotec.ticketing.catalogue.domain.event.EventId;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adapter: implements the application's EventRepository port with Spring Data JPA. */
@Component
class JpaEventRepository implements EventRepository {

  private final EventJpaRepository events;
  private final VenueJpaRepository venues;

  JpaEventRepository(EventJpaRepository events, VenueJpaRepository venues) {
    this.events = events;
    this.venues = venues;
  }

  /**
   * New event: persist. Existing event: load the managed entity, check the version the caller read,
   * copy the domain state onto the entity and let dirty checking write only what changed.
   *
   * <p>The explicit version check matters. The managed entity was just loaded, so it carries the
   * <em>current</em> version; Hibernate's own check only catches a change made between this load
   * and the flush. The caller's domain object may have been read minutes ago, in another
   * transaction, and only its version can tell us whether it is stale.
   */
  @Override
  @Transactional
  public Event save(Event event) {
    var entity = events.findById(event.id().value()).orElse(null);
    if (entity == null) {
      // getReferenceById returns a proxy without a SELECT; the foreign key is all we need.
      entity =
          new EventEntity(event.id().value(), venues.getReferenceById(event.venueId().value()));
      EventMapper.copyToEntity(event, entity);
      events.save(entity); // version is null, so Spring Data calls persist()
    } else {
      if (entity.getVersion() != event.version()) {
        throw new ConcurrentUpdateException("event", event.id(), null);
      }
      EventMapper.copyToEntity(event, entity); // no save() call needed: the entity is managed
    }
    try {
      // Flush now, not at commit, so the SQL (and a version conflict) happens inside this try.
      events.flush();
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ConcurrentUpdateException("event", event.id(), e);
    }
    return EventMapper.toDomain(entity);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Event> findById(EventId id) {
    return events.findById(id.value()).map(EventMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<EventSummary> findSummaries(Set<EventStatus.Code> statuses, PageRequest page) {
    var result =
        events.findSummaries(
            statuses, org.springframework.data.domain.PageRequest.of(page.page(), page.size()));
    return new Page<>(
        result.getContent().stream().map(EventMapper::toSummary).toList(),
        page,
        result.getTotalElements());
  }

  /** Not a port method yet: shows what a JSONB query looks like (used by tests, search later). */
  @Transactional(readOnly = true)
  public List<EventId> findIdsWithSection(String sectionName) {
    return events.findIdsWithSection(sectionName).stream().map(EventId::new).toList();
  }
}
