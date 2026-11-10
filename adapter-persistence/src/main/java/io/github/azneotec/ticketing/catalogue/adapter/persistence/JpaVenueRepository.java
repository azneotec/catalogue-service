package io.github.azneotec.ticketing.catalogue.adapter.persistence;

import io.github.azneotec.ticketing.catalogue.application.port.out.VenueRepository;
import io.github.azneotec.ticketing.catalogue.domain.venue.Venue;
import io.github.azneotec.ticketing.catalogue.domain.venue.VenueId;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adapter: implements the application's VenueRepository port with Spring Data JPA. */
@Component
class JpaVenueRepository implements VenueRepository {

  private final VenueJpaRepository venues;

  JpaVenueRepository(VenueJpaRepository venues) {
    this.venues = venues;
  }

  /**
   * The id is assigned by the domain and the entity has no @Version, so Spring Data cannot tell new
   * from existing and calls merge(): one SELECT, then INSERT or UPDATE.
   */
  @Override
  @Transactional
  public Venue save(Venue venue) {
    venues.save(
        new VenueEntity(venue.id().value(), venue.name(), venue.city(), venue.zone().getId()));
    return venue;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Venue> findById(VenueId id) {
    return venues
        .findById(id.value())
        .map(entity ->
            new Venue(
                new VenueId(entity.getId()),
                entity.getName(),
                entity.getCity(),
                ZoneId.of(entity.getZoneId())));
  }
}
