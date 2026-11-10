package io.github.azneotec.ticketing.catalogue.adapter.rest;

import io.github.azneotec.ticketing.catalogue.application.NotFoundException;
import io.github.azneotec.ticketing.catalogue.application.port.in.CreateEvent;
import io.github.azneotec.ticketing.catalogue.application.port.in.CreateVenue;
import io.github.azneotec.ticketing.catalogue.application.port.in.GetEvent;
import io.github.azneotec.ticketing.catalogue.application.port.in.GetVenue;
import io.github.azneotec.ticketing.catalogue.application.port.in.PublishEvent;
import io.github.azneotec.ticketing.catalogue.domain.event.EventId;
import io.github.azneotec.ticketing.catalogue.domain.venue.VenueId;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Organiser API. Unauthenticated until Keycloak arrives in Phase 2, so {@link
 * LocalhostOnlyAdminInterceptor} lets only loopback callers in (ADR-033).
 */
@RestController
@RequestMapping("/admin")
@Tag(name = "admin", description = "Organiser API; localhost only until Phase 2")
class AdminController {

  private final CreateVenue createVenue;
  private final GetVenue getVenue;
  private final CreateEvent createEvent;
  private final GetEvent getEvent;
  private final PublishEvent publishEvent;

  AdminController(
      CreateVenue createVenue,
      GetVenue getVenue,
      CreateEvent createEvent,
      GetEvent getEvent,
      PublishEvent publishEvent) {
    this.createVenue = createVenue;
    this.getVenue = getVenue;
    this.createEvent = createEvent;
    this.getEvent = getEvent;
    this.publishEvent = publishEvent;
  }

  @PostMapping("/venues")
  ResponseEntity<VenueDto> createVenue(@Valid @RequestBody CreateVenueRequest body) {
    var venue = createVenue.createVenue(body.toCommand());
    return ResponseEntity.created(locationOf(venue.id().value())).body(VenueDto.from(venue));
  }

  @GetMapping("/venues/{id}")
  VenueDto getVenue(@PathVariable UUID id) {
    return getVenue
        .getVenue(new VenueId(id))
        .map(VenueDto::from)
        .orElseThrow(() -> new NotFoundException("venue", id));
  }

  /** Creates a draft. The response's {@code ETag} is what {@code publish} needs in If-Match. */
  @PostMapping("/events")
  ResponseEntity<EventDto> createEvent(@Valid @RequestBody CreateEventRequest body) {
    var event = createEvent.createEvent(body.toCommand());
    return ResponseEntity.created(locationOf(event.id().value()))
        .eTag(ETags.of(event.version()))
        .body(EventDto.from(event));
  }

  /** Any event, drafts included, with its current {@code ETag}. */
  @GetMapping("/events/{id}")
  ResponseEntity<EventDto> getEvent(@PathVariable UUID id) {
    var event =
        getEvent.getEvent(new EventId(id)).orElseThrow(() -> new NotFoundException("event", id));
    return ResponseEntity.ok().eTag(ETags.of(event.version())).body(EventDto.from(event));
  }

  /**
   * Puts a draft on sale, only if it is still the version the caller saw: 428 without If-Match, 412
   * if the event changed since.
   */
  @PostMapping("/events/{id}/publish")
  ResponseEntity<EventDto> publish(
      @PathVariable UUID id,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
    var expectedVersion = ETags.requiredVersion(ifMatch);
    var event = publishEvent.publishEvent(new EventId(id), expectedVersion);
    return ResponseEntity.ok().eTag(ETags.of(event.version())).body(EventDto.from(event));
  }

  private static URI locationOf(UUID id) {
    return ServletUriComponentsBuilder.fromCurrentRequestUri()
        .path("/{id}")
        .buildAndExpand(id)
        .toUri();
  }
}
