package io.github.azneotec.ticketing.catalogue.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** JPA view of the {@code venue} table. Lives only in this adapter; the domain never sees it. */
@Entity
@Table(name = "venue")
class VenueEntity {

  @Id private UUID id;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(nullable = false, length = 100)
  private String city;

  @Column(name = "zone_id", nullable = false, length = 64)
  private String zoneId;

  /** For Hibernate, which creates instances by reflection before filling the fields. */
  protected VenueEntity() {}

  VenueEntity(UUID id, String name, String city, String zoneId) {
    this.id = id;
    this.name = name;
    this.city = city;
    this.zoneId = zoneId;
  }

  /**
   * Public on purpose: Hibernate's lazy proxy answers getId() without a SELECT only if it can
   * override the method. A package-private getter is not overridable from the proxy class, so it
   * initialised the proxy and loaded the venue on every event load (seen in the SQL log).
   */
  public UUID getId() {
    return id;
  }

  String getName() {
    return name;
  }

  String getCity() {
    return city;
  }

  String getZoneId() {
    return zoneId;
  }
}
