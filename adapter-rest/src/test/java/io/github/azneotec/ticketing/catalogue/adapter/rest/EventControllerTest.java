package io.github.azneotec.ticketing.catalogue.adapter.rest;

import static io.github.azneotec.ticketing.catalogue.adapter.rest.Fixtures.EVENT_ID;
import static io.github.azneotec.ticketing.catalogue.adapter.rest.Fixtures.LONDON;
import static io.github.azneotec.ticketing.catalogue.adapter.rest.Fixtures.PUBLISHED_AT;
import static io.github.azneotec.ticketing.catalogue.adapter.rest.Fixtures.STARTS_AT;
import static io.github.azneotec.ticketing.catalogue.adapter.rest.Fixtures.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import io.github.azneotec.ticketing.catalogue.application.EventSummary;
import io.github.azneotec.ticketing.catalogue.application.Page;
import io.github.azneotec.ticketing.catalogue.application.PageRequest;
import io.github.azneotec.ticketing.catalogue.application.port.in.CreateEvent;
import io.github.azneotec.ticketing.catalogue.application.port.in.CreateVenue;
import io.github.azneotec.ticketing.catalogue.application.port.in.GetEvent;
import io.github.azneotec.ticketing.catalogue.application.port.in.GetVenue;
import io.github.azneotec.ticketing.catalogue.application.port.in.ListEvents;
import io.github.azneotec.ticketing.catalogue.application.port.in.PublishEvent;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus.Code;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Public endpoints: HTTP mapping only, use cases are mocks (a web slice, no database). */
@WebMvcTest
class EventControllerTest {

  @Autowired MockMvcTester mvc;
  @MockitoBean GetEvent getEvent;
  @MockitoBean ListEvents listEvents;
  // AdminController is part of the slice too; it needs its ports even when not called.
  @MockitoBean CreateVenue createVenue;
  @MockitoBean GetVenue getVenue;
  @MockitoBean CreateEvent createEvent;
  @MockitoBean PublishEvent publishEvent;

  @Test
  void listsPublishedEventsByDefault() {
    var summary =
        new EventSummary(EVENT_ID, "Concert", STARTS_AT, LONDON, Code.PUBLISHED, "Hall", "London");
    given(listEvents.listEvents(any()))
        .willReturn(new Page<>(List.of(summary), new PageRequest(0, 20), 1));

    var result = mvc.get().uri("/events").exchange();

    assertThat(result).hasStatusOk();
    var json = assertThat(result).bodyJson();
    json.extractingPath("$.items[0].id").isEqualTo(EVENT_ID.toString());
    json.extractingPath("$.items[0].localStartTime").isEqualTo("2026-12-01T19:30:00");
    json.extractingPath("$.items[0].venueName").isEqualTo("Hall");
    json.extractingPath("$.totalItems").isEqualTo(1);
    json.extractingPath("$.totalPages").isEqualTo(1);
    then(listEvents)
        .should()
        .listEvents(new ListEvents.Query(Set.of(Code.PUBLISHED), new PageRequest(0, 20)));
  }

  @Test
  void passesStatusesAndPaging() {
    given(listEvents.listEvents(any())).willReturn(new Page<>(List.of(), new PageRequest(2, 5), 0));

    assertThat(mvc.get().uri("/events?status=PUBLISHED,CANCELLED&page=2&size=5")).hasStatusOk();

    then(listEvents)
        .should()
        .listEvents(
            new ListEvents.Query(Set.of(Code.PUBLISHED, Code.CANCELLED), new PageRequest(2, 5)));
  }

  @Test
  void refusesToListDrafts() {
    assertThat(mvc.get().uri("/events?status=DRAFT"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:ticketing:problem:draft-not-public");
  }

  @Test
  void rejectsBadPagingWithFieldErrors() {
    var result = mvc.get().uri("/events?size=500&page=-1").exchange();

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    var json = assertThat(result).bodyJson();
    json.extractingPath("$.type").isEqualTo("urn:ticketing:problem:validation");
    json.extractingPath("$.errors[*].field").asArray().containsExactlyInAnyOrder("size", "page");
  }

  @Test
  void rejectsAnUnknownStatus() {
    assertThat(mvc.get().uri("/events?status=SOLD_OUT")).hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void returnsOneEventWithItsVersionAsETag() {
    given(getEvent.getEvent(EVENT_ID))
        .willReturn(Optional.of(event(new EventStatus.Published(PUBLISHED_AT), 3)));

    var result = mvc.get().uri("/events/{id}", EVENT_ID.value()).exchange();

    assertThat(result).hasStatusOk().headers().hasValue(HttpHeaders.ETAG, "\"3\"");
    var json = assertThat(result).bodyJson();
    json.extractingPath("$.status").isEqualTo("PUBLISHED");
    json.extractingPath("$.publishedAt").isEqualTo("2026-11-11T09:00:00Z");
    json.extractingPath("$.capacity").isEqualTo(3);
    json.extractingPath("$.seatMap.sections[0].rows[0].seats").asArray().containsExactly("1", "2");
    json.extractingPath("$.zone").isEqualTo("Europe/London");
    assertThat(result).bodyText().doesNotContain("cancelledAt");
  }

  @Test
  void answersNotModifiedWhenTheClientHasTheCurrentVersion() {
    given(getEvent.getEvent(EVENT_ID))
        .willReturn(Optional.of(event(new EventStatus.Published(PUBLISHED_AT), 3)));

    assertThat(
            mvc.get()
                .uri("/events/{id}", EVENT_ID.value())
                .header(HttpHeaders.IF_NONE_MATCH, "\"3\""))
        .hasStatus(HttpStatus.NOT_MODIFIED);
  }

  @Test
  void showsCancelledEventsWithTheReason() {
    given(getEvent.getEvent(EVENT_ID))
        .willReturn(Optional.of(event(new EventStatus.Cancelled(PUBLISHED_AT, "Storm"), 4)));

    assertThat(mvc.get().uri("/events/{id}", EVENT_ID.value()))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.cancelReason")
        .isEqualTo("Storm");
  }

  @Test
  void hidesDraftsAsNotFound() {
    given(getEvent.getEvent(EVENT_ID)).willReturn(Optional.of(event(new EventStatus.Draft(), 0)));

    assertThat(mvc.get().uri("/events/{id}", EVENT_ID.value()))
        .hasStatus(HttpStatus.NOT_FOUND)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:ticketing:problem:not-found");
  }

  @Test
  void unknownEventIsNotFoundAsProblemDetails() {
    given(getEvent.getEvent(EVENT_ID)).willReturn(Optional.empty());

    var result = mvc.get().uri("/events/{id}", EVENT_ID.value()).exchange();

    assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentType("application/problem+json");
    var json = assertThat(result).bodyJson();
    json.extractingPath("$.status").isEqualTo(404);
    json.extractingPath("$.title").isEqualTo("Not found");
    json.extractingPath("$.detail").isEqualTo("event " + EVENT_ID + " not found");
    json.extractingPath("$.instance").isEqualTo("/events/" + EVENT_ID);
  }

  @Test
  void malformedIdIsBadRequest() {
    assertThat(mvc.get().uri("/events/not-a-uuid")).hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void corsAllowsTheWebShellToReadPublicEndpoints() {
    assertThat(
            mvc.options()
                .uri("/events")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .hasStatusOk()
        .headers()
        .hasValue(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");
  }

  @Test
  void corsRejectsOtherOriginsAndTheAdminApi() {
    assertThat(
            mvc.options()
                .uri("/events")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .hasStatus(HttpStatus.FORBIDDEN);
    // No CORS mapping for /admin: Spring answers the OPTIONS call without any
    // Access-Control-Allow-* header, and the browser then refuses to send the real request.
    assertThat(
            mvc.options()
                .uri("/admin/venues")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .headers()
        .doesNotContainHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
  }
}
