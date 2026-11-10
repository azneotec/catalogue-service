package io.github.azneotec.ticketing.catalogue.adapter.rest;

import static io.github.azneotec.ticketing.catalogue.adapter.rest.Fixtures.EVENT_ID;
import static io.github.azneotec.ticketing.catalogue.adapter.rest.Fixtures.PUBLISHED_AT;
import static io.github.azneotec.ticketing.catalogue.adapter.rest.Fixtures.SEAT_MAP;
import static io.github.azneotec.ticketing.catalogue.adapter.rest.Fixtures.SEAT_MAP_JSON;
import static io.github.azneotec.ticketing.catalogue.adapter.rest.Fixtures.STARTS_AT;
import static io.github.azneotec.ticketing.catalogue.adapter.rest.Fixtures.VENUE_ID;
import static io.github.azneotec.ticketing.catalogue.adapter.rest.Fixtures.event;
import static io.github.azneotec.ticketing.catalogue.adapter.rest.Fixtures.venue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import io.github.azneotec.ticketing.catalogue.application.ConcurrentUpdateException;
import io.github.azneotec.ticketing.catalogue.application.NotFoundException;
import io.github.azneotec.ticketing.catalogue.application.port.in.CreateEvent;
import io.github.azneotec.ticketing.catalogue.application.port.in.CreateVenue;
import io.github.azneotec.ticketing.catalogue.application.port.in.GetEvent;
import io.github.azneotec.ticketing.catalogue.application.port.in.GetVenue;
import io.github.azneotec.ticketing.catalogue.application.port.in.ListEvents;
import io.github.azneotec.ticketing.catalogue.application.port.in.PublishEvent;
import io.github.azneotec.ticketing.catalogue.domain.IllegalTransitionException;
import io.github.azneotec.ticketing.catalogue.domain.InvalidValueException;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Admin endpoints, the localhost-only guard and the error mapping. */
@WebMvcTest
class AdminControllerTest {

  private static final String CREATE_EVENT_JSON =
      """
      {"title":"Concert","venueId":"%s","startsAt":"2026-12-01T19:30:00Z","seatMap":%s}\
      """
          .formatted(VENUE_ID, SEAT_MAP_JSON);

  @Autowired MockMvcTester mvc;
  @MockitoBean CreateVenue createVenue;
  @MockitoBean GetVenue getVenue;
  @MockitoBean CreateEvent createEvent;
  @MockitoBean GetEvent getEvent;
  @MockitoBean PublishEvent publishEvent;
  @MockitoBean ListEvents listEvents;

  private static RequestPostProcessor from(String remoteAddress) {
    return request -> {
      request.setRemoteAddr(remoteAddress);
      return request;
    };
  }

  @Test
  void createsAVenue() {
    given(createVenue.createVenue(any())).willReturn(venue());

    var result =
        mvc.post()
            .uri("/admin/venues")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Hall\",\"city\":\"London\",\"zone\":\"Europe/London\"}")
            .exchange();

    assertThat(result)
        .hasStatus(HttpStatus.CREATED)
        .headers()
        .hasValue(HttpHeaders.LOCATION, "http://localhost/admin/venues/" + VENUE_ID);
    assertThat(result).bodyJson().extractingPath("$.zone").isEqualTo("Europe/London");
    then(createVenue)
        .should()
        .createVenue(new CreateVenue.Command("Hall", "London", Fixtures.LONDON));
  }

  @Test
  void listsEveryInvalidField() {
    var result =
        mvc.post()
            .uri("/admin/venues")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\" \",\"zone\":\"Europe/London\"}")
            .exchange();

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    var json = assertThat(result).bodyJson();
    json.extractingPath("$.title").isEqualTo("Validation failed");
    json.extractingPath("$.errors[*].field").asArray().containsExactlyInAnyOrder("name", "city");
  }

  @Test
  void unreadableJsonIsBadRequest() {
    assertThat(
            mvc.post()
                .uri("/admin/venues")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Hall\",\"city\":\"London\",\"zone\":\"Mars/Olympus\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void domainRuleViolationsAreBadRequest() {
    given(createVenue.createVenue(any()))
        .willThrow(new InvalidValueException("venue name must be at most 200 characters"));

    var result =
        mvc.post()
            .uri("/admin/venues")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Hall\",\"city\":\"London\",\"zone\":\"Europe/London\"}")
            .exchange();

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("venue name must be at most 200 characters");
  }

  @Test
  void getsAVenueOrNotFound() {
    given(getVenue.getVenue(VENUE_ID)).willReturn(Optional.of(venue()));
    assertThat(mvc.get().uri("/admin/venues/{id}", VENUE_ID.value()))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.name")
        .isEqualTo("Hall");

    given(getVenue.getVenue(VENUE_ID)).willReturn(Optional.empty());
    assertThat(mvc.get().uri("/admin/venues/{id}", VENUE_ID.value()))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void createsAnEventWithLocationAndETag() {
    given(createEvent.createEvent(any())).willReturn(event(new EventStatus.Draft(), 0));

    var result =
        mvc.post()
            .uri("/admin/events")
            .contentType(MediaType.APPLICATION_JSON)
            .content(CREATE_EVENT_JSON)
            .exchange();

    assertThat(result)
        .hasStatus(HttpStatus.CREATED)
        .headers()
        .hasValue(HttpHeaders.LOCATION, "http://localhost/admin/events/" + EVENT_ID)
        .hasValue(HttpHeaders.ETAG, "\"0\"");
    assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("DRAFT");
    then(createEvent)
        .should()
        .createEvent(new CreateEvent.Command("Concert", VENUE_ID, STARTS_AT, SEAT_MAP));
  }

  @Test
  void validatesTheNestedSeatMap() {
    var result =
        mvc.post()
            .uri("/admin/events")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"title":"Concert","venueId":"%s","startsAt":"2026-12-01T19:30:00Z",
                 "seatMap":{"sections":[{"name":"Stalls","rows":[]}]}}\
                """
                    .formatted(VENUE_ID))
            .exchange();

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .isEqualTo("seatMap.sections[0].rows");
  }

  @Test
  void getsAnyEventIncludingDraftsWithETag() {
    given(getEvent.getEvent(EVENT_ID)).willReturn(Optional.of(event(new EventStatus.Draft(), 2)));

    assertThat(mvc.get().uri("/admin/events/{id}", EVENT_ID.value()))
        .hasStatusOk()
        .headers()
        .hasValue(HttpHeaders.ETAG, "\"2\"");

    given(getEvent.getEvent(EVENT_ID)).willReturn(Optional.empty());
    assertThat(mvc.get().uri("/admin/events/{id}", EVENT_ID.value()))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void publishesWithIfMatchAndReturnsTheNewETag() {
    given(publishEvent.publishEvent(EVENT_ID, 0))
        .willReturn(event(new EventStatus.Published(PUBLISHED_AT), 1));

    assertThat(
            mvc.post()
                .uri("/admin/events/{id}/publish", EVENT_ID.value())
                .header(HttpHeaders.IF_MATCH, "\"0\""))
        .hasStatusOk()
        .headers()
        .hasValue(HttpHeaders.ETAG, "\"1\"");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "*"})
  void publishWithoutASpecificIfMatchIsPreconditionRequired(String ifMatch) {
    var request = mvc.post().uri("/admin/events/{id}/publish", EVENT_ID.value());
    if (!ifMatch.isEmpty()) {
      request.header(HttpHeaders.IF_MATCH, ifMatch);
    }

    assertThat(request)
        .hasStatus(HttpStatus.PRECONDITION_REQUIRED)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:ticketing:problem:if-match-required");
    then(publishEvent).should(org.mockito.Mockito.never()).publishEvent(any(), anyLong());
  }

  @ParameterizedTest
  @ValueSource(strings = {"3", "W/\"3\"", "\"abc\"", "\"1\", \"2\""})
  void publishWithAMalformedIfMatchIsBadRequest(String ifMatch) {
    assertThat(
            mvc.post()
                .uri("/admin/events/{id}/publish", EVENT_ID.value())
                .header(HttpHeaders.IF_MATCH, ifMatch))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:ticketing:problem:invalid-if-match");
  }

  @Test
  void publishWithAStaleIfMatchIsPreconditionFailed() {
    given(publishEvent.publishEvent(EVENT_ID, 0))
        .willThrow(new ConcurrentUpdateException("event", EVENT_ID));

    assertThat(
            mvc.post()
                .uri("/admin/events/{id}/publish", EVENT_ID.value())
                .header(HttpHeaders.IF_MATCH, "\"0\""))
        .hasStatus(HttpStatus.PRECONDITION_FAILED)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:ticketing:problem:stale-version");
  }

  @Test
  void anIllegalTransitionIsConflict() {
    given(publishEvent.publishEvent(EVENT_ID, 4))
        .willThrow(new IllegalTransitionException("event is cancelled and cannot be published"));

    assertThat(
            mvc.post()
                .uri("/admin/events/{id}/publish", EVENT_ID.value())
                .header(HttpHeaders.IF_MATCH, "\"4\""))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:ticketing:problem:illegal-transition");
  }

  @Test
  void publishingAnUnknownEventIsNotFound() {
    given(publishEvent.publishEvent(EVENT_ID, 0))
        .willThrow(new NotFoundException("event", EVENT_ID));

    assertThat(
            mvc.post()
                .uri("/admin/events/{id}/publish", EVENT_ID.value())
                .header(HttpHeaders.IF_MATCH, "\"0\""))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @ParameterizedTest
  @ValueSource(strings = {"203.0.113.7", "192.168.1.20", "::ffff:10.0.0.1"})
  void adminCallsFromAnotherMachineAreForbidden(String remoteAddress) {
    var result =
        mvc.post()
            .uri("/admin/venues")
            .with(from(remoteAddress))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Hall\",\"city\":\"London\",\"zone\":\"Europe/London\"}")
            .exchange();

    assertThat(result)
        .hasStatus(HttpStatus.FORBIDDEN)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:ticketing:problem:admin-localhost-only");
    then(createVenue).shouldHaveNoInteractions();
  }

  @Test
  void aForwardedHeaderCannotFakeLocalhost() {
    assertThat(
            mvc.get()
                .uri("/admin/venues/{id}", VENUE_ID.value())
                .with(from("203.0.113.7"))
                .header("X-Forwarded-For", "127.0.0.1"))
        .hasStatus(HttpStatus.FORBIDDEN);
  }

  @Test
  void ipv6LoopbackIsLocal() {
    given(getVenue.getVenue(VENUE_ID)).willReturn(Optional.of(venue()));

    assertThat(mvc.get().uri("/admin/venues/{id}", VENUE_ID.value()).with(from("0:0:0:0:0:0:0:1")))
        .hasStatusOk();
  }

  @Test
  void publicEndpointsAcceptAnyCaller() {
    given(getEvent.getEvent(EVENT_ID))
        .willReturn(Optional.of(event(new EventStatus.Published(PUBLISHED_AT), 1)));

    assertThat(mvc.get().uri("/events/{id}", EVENT_ID.value()).with(from("203.0.113.7")))
        .hasStatusOk();
  }
}
