package io.github.azneotec.ticketing.catalogue.adapter.rest;

import io.github.azneotec.ticketing.catalogue.application.NotFoundException;
import io.github.azneotec.ticketing.catalogue.application.port.in.CreateEvent;
import io.github.azneotec.ticketing.catalogue.application.port.in.CreateVenue;
import io.github.azneotec.ticketing.catalogue.application.port.in.GetEvent;
import io.github.azneotec.ticketing.catalogue.application.port.in.GetVenue;
import io.github.azneotec.ticketing.catalogue.application.port.in.PublishEvent;
import io.github.azneotec.ticketing.catalogue.domain.event.EventId;
import io.github.azneotec.ticketing.catalogue.domain.venue.VenueId;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
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
@RequestMapping(path = "/admin", produces = MediaType.APPLICATION_JSON_VALUE)
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
  @ApiResponse(responseCode = "201", description = "Created; Location points at the venue")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid venue",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  ResponseEntity<VenueDto> createVenue(@Valid @RequestBody CreateVenueRequest body) {
    var venue = createVenue.createVenue(body.toCommand());
    return ResponseEntity.created(locationOf(venue.id().value())).body(VenueDto.from(venue));
  }

  @GetMapping("/venues/{id}")
  @ApiResponse(responseCode = "200", description = "The venue")
  @ApiResponse(
      responseCode = "404",
      description = "No such venue",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  VenueDto getVenue(@PathVariable UUID id) {
    return getVenue
        .getVenue(new VenueId(id))
        .map(VenueDto::from)
        .orElseThrow(() -> new NotFoundException("venue", id));
  }

  /** Creates a draft. The response's {@code ETag} is what {@code publish} needs in If-Match. */
  @PostMapping("/events")
  @ApiResponse(
      responseCode = "201",
      description = "Created as a draft; Location and ETag set",
      headers = @Header(name = "ETag", description = "Version of the event"))
  @ApiResponse(
      responseCode = "400",
      description = "Invalid event",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "No such venue",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  ResponseEntity<EventDto> createEvent(@Valid @RequestBody CreateEventRequest body) {
    var event = createEvent.createEvent(body.toCommand());
    return ResponseEntity.created(locationOf(event.id().value()))
        .eTag(ETags.of(event.version()))
        .body(EventDto.from(event));
  }

  /** Any event, drafts included, with its current {@code ETag}. */
  @GetMapping("/events/{id}")
  @ApiResponse(
      responseCode = "200",
      description = "The event",
      headers = @Header(name = "ETag", description = "Version of the event"))
  @ApiResponse(
      responseCode = "404",
      description = "No such event",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
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
  @ApiResponse(
      responseCode = "200",
      description = "Published",
      headers = @Header(name = "ETag", description = "New version of the event"))
  @ApiResponse(
      responseCode = "400",
      description = "Malformed If-Match",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "No such event",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Not a draft, or starts in the past",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "412",
      description = "Stale If-Match: the event changed",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "428",
      description = "If-Match missing",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
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
