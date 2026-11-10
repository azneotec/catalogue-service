package io.github.azneotec.ticketing.catalogue.adapter.rest;

import io.github.azneotec.ticketing.catalogue.application.NotFoundException;
import io.github.azneotec.ticketing.catalogue.application.PageRequest;
import io.github.azneotec.ticketing.catalogue.application.port.in.GetEvent;
import io.github.azneotec.ticketing.catalogue.application.port.in.ListEvents;
import io.github.azneotec.ticketing.catalogue.domain.event.EventId;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/** Public, read-only API for buyers. Drafts are invisible here: they exist only for organisers. */
@RestController
@RequestMapping(path = "/events", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "events", description = "Public event catalogue")
class EventController {

  private final GetEvent getEvent;
  private final ListEvents listEvents;

  EventController(GetEvent getEvent, ListEvents listEvents) {
    this.getEvent = getEvent;
    this.listEvents = listEvents;
  }

  /** Events in the given states (published by default), soonest first. */
  @GetMapping
  @ApiResponse(responseCode = "200", description = "A page of events")
  @ApiResponse(
      responseCode = "400",
      description = "Bad status or paging",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  PageDto<EventSummaryDto> list(
      @RequestParam(defaultValue = "PUBLISHED") Set<EventStatus.Code> status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(PageRequest.MAX_SIZE) int size) {
    if (status.contains(EventStatus.Code.DRAFT)) {
      throw Problems.exception(
          HttpStatus.BAD_REQUEST,
          "draft-not-public",
          "Drafts are not public",
          "status may be PUBLISHED or CANCELLED; drafts are only visible to organisers");
    }
    var result = listEvents.listEvents(new ListEvents.Query(status, new PageRequest(page, size)));
    return PageDto.from(result, EventSummaryDto::from);
  }

  /**
   * One event with its seat map. The {@code ETag} is its version; a client that sends it back in
   * {@code If-None-Match} gets {@code 304 Not Modified} without a body while nothing has changed.
   */
  @GetMapping("/{id}")
  @ApiResponse(
      responseCode = "200",
      description = "The event",
      headers = @Header(name = "ETag", description = "Version of the event, e.g. \"3\""))
  @ApiResponse(responseCode = "304", description = "Not modified since the If-None-Match ETag")
  @ApiResponse(
      responseCode = "404",
      description = "No such published or cancelled event",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  ResponseEntity<EventDto> get(@PathVariable UUID id, WebRequest request) {
    var event =
        getEvent
            .getEvent(new EventId(id))
            .filter(found -> found.status().code() != EventStatus.Code.DRAFT)
            .orElseThrow(() -> new NotFoundException("event", id));
    var etag = ETags.of(event.version());
    if (request.checkNotModified(etag)) {
      return null; // Spring has already set 304 and the ETag
    }
    return ResponseEntity.ok().eTag(etag).body(EventDto.from(event));
  }
}
