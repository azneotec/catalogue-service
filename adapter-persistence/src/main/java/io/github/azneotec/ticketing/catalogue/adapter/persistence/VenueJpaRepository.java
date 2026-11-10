package io.github.azneotec.ticketing.catalogue.adapter.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data generates the implementation at startup. */
interface VenueJpaRepository extends JpaRepository<VenueEntity, UUID> {}
