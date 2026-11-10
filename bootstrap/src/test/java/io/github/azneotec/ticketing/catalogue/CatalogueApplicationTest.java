package io.github.azneotec.ticketing.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.azneotec.ticketing.catalogue.application.PageRequest;
import io.github.azneotec.ticketing.catalogue.application.port.in.CreateEvent;
import io.github.azneotec.ticketing.catalogue.application.port.in.CreateVenue;
import io.github.azneotec.ticketing.catalogue.application.port.in.GetEvent;
import io.github.azneotec.ticketing.catalogue.application.port.in.ListEvents;
import io.github.azneotec.ticketing.catalogue.application.port.in.PublishEvent;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Row;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Seat;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.SeatMap;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Section;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationHook;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The whole service, wired by Spring, against a real Postgres: migrations, use cases, health. */
class CatalogueApplicationTest extends IntegrationTestBase {

  @LocalServerPort int port;
  @Autowired JdbcClient jdbc;
  @Autowired CreateVenue createVenue;
  @Autowired CreateEvent createEvent;
  @Autowired PublishEvent publishEvent;
  @Autowired GetEvent getEvent;
  @Autowired ListEvents listEvents;

  @Test
  void flywayAppliedBothMigrations() {
    var versions =
        jdbc.sql("select version from flyway_schema_history where success order by installed_rank")
            .query(String.class)
            .list();
    assertThat(versions).containsExactly("1", "2");
  }

  @Test
  void useCasesWorkEndToEnd() {
    var venue =
        createVenue.createVenue(
            new CreateVenue.Command("Hall", "London", ZoneId.of("Europe/London")));
    var seatMap =
        new SeatMap(List.of(new Section("Stalls", List.of(new Row("A", List.of(new Seat("1")))))));
    var event =
        createEvent.createEvent(
            new CreateEvent.Command(
                "Concert", venue.id(), Instant.now().plus(Duration.ofDays(30)), seatMap));

    publishEvent.publishEvent(event.id(), event.version());

    assertThat(getEvent.getEvent(event.id()).orElseThrow().status())
        .isInstanceOf(EventStatus.Published.class);
    assertThat(
            listEvents
                .listEvents(
                    new ListEvents.Query(
                        Set.of(EventStatus.Code.PUBLISHED), new PageRequest(0, 10)))
                .items())
        .extracting(s -> s.title())
        .contains("Concert");
  }

  @Test
  void actuatorHealthIsUp() throws Exception {
    try (var http = HttpClient.newHttpClient()) {
      var response =
          http.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("\"status\":\"UP\"");
    }
  }

  @Test
  void mainStartsAndStopsCleanly() {
    // SpringApplication.withHook observes the SpringApplication that main() creates, so the test
    // can get hold of the started context and close it, the same way SIGTERM would.
    var started = new AtomicReference<ConfigurableApplicationContext>();
    SpringApplicationHook hook =
        application ->
            new SpringApplicationRunListener() {
              @Override
              public void ready(ConfigurableApplicationContext context, Duration timeTaken) {
                started.set(context);
              }
            };
    SpringApplication.withHook(
        hook,
        () ->
            CatalogueApplication.main(
                new String[] {
                  "--server.port=0",
                  "--spring.grpc.server.port=0",
                  "--management.tracing.export.otlp.enabled=false",
                  "--management.otlp.metrics.export.enabled=false",
                  "--management.logging.export.otlp.enabled=false",
                  "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                  "--spring.datasource.username=" + POSTGRES.getUsername(),
                  "--spring.datasource.password=" + POSTGRES.getPassword()
                }));

    var context = started.get();
    assertThat(context.isRunning()).isTrue();
    context.close();
    assertThat(context.isActive()).isFalse();
  }
}
