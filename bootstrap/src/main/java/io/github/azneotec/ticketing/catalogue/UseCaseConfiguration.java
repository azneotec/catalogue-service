package io.github.azneotec.ticketing.catalogue;

import io.github.azneotec.ticketing.catalogue.application.port.out.EventRepository;
import io.github.azneotec.ticketing.catalogue.application.port.out.VenueRepository;
import io.github.azneotec.ticketing.catalogue.application.service.EventService;
import io.github.azneotec.ticketing.catalogue.application.service.VenueService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the plain-Java use cases into Spring. The application module has no Spring annotations, so
 * the composition happens here, at the edge, where frameworks are allowed.
 */
@Configuration(proxyBeanMethods = false)
class UseCaseConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  VenueService venueService(VenueRepository venues) {
    return new VenueService(venues);
  }

  @Bean
  EventService eventService(EventRepository events, VenueRepository venues, Clock clock) {
    return new EventService(events, venues, clock);
  }
}
