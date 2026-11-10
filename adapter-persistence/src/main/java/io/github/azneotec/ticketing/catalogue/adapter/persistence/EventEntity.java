package io.github.azneotec.ticketing.catalogue.adapter.persistence;

import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA view of the {@code event} table. Mutable with setters because that is how JPA works (dirty
 * checking compares field values); the domain {@code Event} stays free of all of this.
 */
@Entity
@Table(name = "event")
class EventEntity {

  @Id private UUID id;

  /**
   * LAZY: loading an event does not load its venue. Hibernate puts a proxy here and only queries
   * the venue if a getter other than getId() is called. (JPA's default for @ManyToOne is EAGER,
   * which would add a join or an extra query to every event load.)
   */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "venue_id", nullable = false, updatable = false)
  private VenueEntity venue;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "zone_id", nullable = false, length = 64)
  private String zoneId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private EventStatus.Code status;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancel_reason", length = 500)
  private String cancelReason;

  /** Hibernate serialises this record to JSON and binds it as {@code jsonb}. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "seat_map", nullable = false)
  private SeatMapDocument seatMap;

  /**
   * Optimistic locking. Hibernate adds {@code where version = ?} to every UPDATE and increments it;
   * if no row matched, someone else updated first and the flush fails. Null means "not saved yet",
   * which is also how Spring Data's save() decides between persist and merge.
   */
  @Version private Long version;

  protected EventEntity() {}

  EventEntity(UUID id, VenueEntity venue) {
    this.id = id;
    this.venue = venue;
  }

  UUID getId() {
    return id;
  }

  VenueEntity getVenue() {
    return venue;
  }

  String getTitle() {
    return title;
  }

  void setTitle(String title) {
    this.title = title;
  }

  Instant getStartsAt() {
    return startsAt;
  }

  void setStartsAt(Instant startsAt) {
    this.startsAt = startsAt;
  }

  String getZoneId() {
    return zoneId;
  }

  void setZoneId(String zoneId) {
    this.zoneId = zoneId;
  }

  EventStatus.Code getStatus() {
    return status;
  }

  Instant getPublishedAt() {
    return publishedAt;
  }

  Instant getCancelledAt() {
    return cancelledAt;
  }

  String getCancelReason() {
    return cancelReason;
  }

  /** The status columns change together, so they are set together. */
  void setStatus(
      EventStatus.Code status, Instant publishedAt, Instant cancelledAt, String cancelReason) {
    this.status = status;
    this.publishedAt = publishedAt;
    this.cancelledAt = cancelledAt;
    this.cancelReason = cancelReason;
  }

  SeatMapDocument getSeatMap() {
    return seatMap;
  }

  void setSeatMap(SeatMapDocument seatMap) {
    this.seatMap = seatMap;
  }

  Long getVersion() {
    return version;
  }
}
