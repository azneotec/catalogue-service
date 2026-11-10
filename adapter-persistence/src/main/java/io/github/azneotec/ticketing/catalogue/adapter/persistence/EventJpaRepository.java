package io.github.azneotec.ticketing.catalogue.adapter.persistence;

import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EventJpaRepository extends JpaRepository<EventEntity, UUID> {

  /**
   * One SQL query joins event and venue and selects only the listed columns: no seat map, no
   * entities, no lazy loading, so no N+1. Spring Data runs the count query for the page total.
   */
  @Query(
      value =
          """
          select new io.github.azneotec.ticketing.catalogue.adapter.persistence.EventSummaryRow(
              e.id, e.title, e.startsAt, e.zoneId, e.status, v.name, v.city)
          from EventEntity e join e.venue v
          where e.status in :statuses
          order by e.startsAt, e.id
          """,
      countQuery = "select count(e) from EventEntity e where e.status in :statuses")
  Page<EventSummaryRow> findSummaries(
      @Param("statuses") Collection<EventStatus.Code> statuses, Pageable pageable);

  /**
   * Ids of events whose seat map has a section with this name. A native query, because JPQL knows
   * nothing about JSONB. {@code @>} means "contains this JSON fragment" and can use the GIN index.
   */
  @Query(
      value =
          """
          select e.id from event e
          where e.seat_map @> jsonb_build_object(
              'sections', jsonb_build_array(jsonb_build_object('name', cast(:section as text))))
          order by e.starts_at
          """,
      nativeQuery = true)
  List<UUID> findIdsWithSection(@Param("section") String section);
}
