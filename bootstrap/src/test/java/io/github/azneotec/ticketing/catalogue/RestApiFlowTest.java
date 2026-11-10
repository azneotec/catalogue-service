package io.github.azneotec.ticketing.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * The REST API over real HTTP, real JSON and a real Postgres: the organiser flow from venue to
 * published event, and the optimistic-locking rules on the way.
 */
class RestApiFlowTest extends IntegrationTestBase {

  @LocalServerPort int port;
  private RestClient http;

  @BeforeEach
  void client() {
    http =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            // Return every status as a response instead of throwing, so tests can assert on it.
            .defaultStatusHandler(status -> true, (request, response) -> {})
            .build();
  }

  @Test
  void organiserCreatesAndPublishesAnEventThatBuyersThenSee() {
    // 1. Venue: 201 with a Location that resolves.
    var venue =
        post(
            "/admin/venues",
            Map.of("name", "Royal Hall", "city", "London", "zone", "Europe/London"),
            null);
    assertThat(venue.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    var venueLocation = venue.getHeaders().getLocation();
    assertThat(http.get().uri(venueLocation).retrieve().toEntity(Map.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var venueId = venue.getBody().get("id");

    // 2. Draft event: 201 with Location and ETag "0".
    var startsAt = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    var created =
        post(
            "/admin/events",
            Map.of(
                "title",
                "Flow Test Concert",
                "venueId",
                venueId,
                "startsAt",
                startsAt.toString(),
                "seatMap",
                Map.of(
                    "sections",
                    java.util.List.of(
                        Map.of(
                            "name",
                            "Stalls",
                            "rows",
                            java.util.List.of(
                                Map.of("label", "A", "seats", java.util.List.of("1", "2"))))))),
            null);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getHeaders().getETag()).isEqualTo("\"0\"");
    var eventId = created.getBody().get("id");

    // 3. Buyers can't see a draft.
    assertThat(get("/events/" + eventId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    // 4. Publishing needs If-Match.
    assertThat(post("/admin/events/" + eventId + "/publish", null, null).getStatusCode())
        .isEqualTo(HttpStatus.PRECONDITION_REQUIRED);

    // 5. Publish with the ETag we got: 200, new ETag "1".
    var published = post("/admin/events/" + eventId + "/publish", null, "\"0\"");
    assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(published.getHeaders().getETag()).isEqualTo("\"1\"");
    assertThat(published.getBody()).containsEntry("status", "PUBLISHED");

    // 6. Buyers see it, in the list and on its own, with the same ETag.
    var list = get("/events?size=100");
    assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(list.getBody().get("items").toString()).contains("Flow Test Concert");
    var detail = get("/events/" + eventId);
    assertThat(detail.getHeaders().getETag()).isEqualTo("\"1\"");
    assertThat(detail.getBody()).containsEntry("capacity", 2);

    // 7. A client still holding version 0 is rejected: precondition failed, nothing changes.
    var stale = post("/admin/events/" + eventId + "/publish", null, "\"0\"");
    assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
    assertThat(stale.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(stale.getBody()).containsEntry("type", "urn:ticketing:problem:stale-version");

    // 8. With the current version the request is understood, but the state forbids it: 409.
    var again = post("/admin/events/" + eventId + "/publish", null, "\"1\"");
    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private ResponseEntity<Map<String, Object>> post(String path, Object body, String ifMatch) {
    var request = http.post().uri(path);
    if (ifMatch != null) {
      request.header(HttpHeaders.IF_MATCH, ifMatch);
    }
    if (body != null) {
      request.contentType(MediaType.APPLICATION_JSON).body(body);
    }
    return (ResponseEntity) request.retrieve().toEntity(Map.class);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private ResponseEntity<Map<String, Object>> get(String path) {
    return (ResponseEntity) http.get().uri(path).retrieve().toEntity(Map.class);
  }
}
