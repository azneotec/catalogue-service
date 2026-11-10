package io.github.azneotec.ticketing.catalogue.adapter.persistence;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A minimal Spring Boot app for the adapter's own tests. Living in the adapter's package, it scans
 * just this adapter: its entities, Spring Data repositories and @Component adapters.
 */
@SpringBootApplication
class PersistenceTestApplication {}
