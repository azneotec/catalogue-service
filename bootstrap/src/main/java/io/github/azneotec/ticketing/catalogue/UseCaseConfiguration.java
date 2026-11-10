package io.github.azneotec.ticketing.catalogue;

import io.github.azneotec.ticketing.catalogue.application.port.in.CreateEvent;
import io.github.azneotec.ticketing.catalogue.application.port.in.CreateVenue;
import io.github.azneotec.ticketing.catalogue.application.port.in.GetEvent;
import io.github.azneotec.ticketing.catalogue.application.port.in.GetVenue;
import io.github.azneotec.ticketing.catalogue.application.port.in.ListEvents;
import io.github.azneotec.ticketing.catalogue.application.port.in.PublishEvent;
import io.github.azneotec.ticketing.catalogue.application.port.out.EventRepository;
import io.github.azneotec.ticketing.catalogue.application.port.out.VenueRepository;
import io.github.azneotec.ticketing.catalogue.application.service.EventService;
import io.github.azneotec.ticketing.catalogue.application.service.VenueService;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Wires the plain-Java use cases into Spring. The application module has no Spring annotations, so
 * the composition happens here, at the edge, where frameworks are allowed.
 *
 * <p>Each inbound port is published as an observed wrapper ({@link ObservedUseCases}). The wrappers
 * are {@code @Primary}, so adapters asking for a port get the observed one, not the bare service.
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

  @Bean
  @Primary
  CreateVenue observedCreateVenue(VenueService service, ObservationRegistry registry) {
    return ObservedUseCases.observe(CreateVenue.class, service, registry);
  }

  @Bean
  @Primary
  GetVenue observedGetVenue(VenueService service, ObservationRegistry registry) {
    return ObservedUseCases.observe(GetVenue.class, service, registry);
  }

  @Bean
  @Primary
  CreateEvent observedCreateEvent(EventService service, ObservationRegistry registry) {
    return ObservedUseCases.observe(CreateEvent.class, service, registry);
  }

  @Bean
  @Primary
  PublishEvent observedPublishEvent(EventService service, ObservationRegistry registry) {
    return ObservedUseCases.observe(PublishEvent.class, service, registry);
  }

  @Bean
  @Primary
  GetEvent observedGetEvent(EventService service, ObservationRegistry registry) {
    return ObservedUseCases.observe(GetEvent.class, service, registry);
  }

  @Bean
  @Primary
  ListEvents observedListEvents(EventService service, ObservationRegistry registry) {
    return ObservedUseCases.observe(ListEvents.class, service, registry);
  }
}
