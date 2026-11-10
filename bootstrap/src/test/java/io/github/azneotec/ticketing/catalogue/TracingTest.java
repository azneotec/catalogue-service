package io.github.azneotec.ticketing.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.azneotec.ticketing.catalogue.application.port.in.CreateEvent;
import io.github.azneotec.ticketing.catalogue.application.port.in.CreateVenue;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Row;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Seat;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.SeatMap;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Section;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

/**
 * One HTTP request gives one trace: the HTTP server span, the use-case span inside it, and the SQL
 * spans inside that. Spans go to an in-memory exporter instead of OTLP.
 */
@AutoConfigureTracing
@TestPropertySource(properties = "management.opentelemetry.tracing.export.schedule-delay=50ms")
class TracingTest extends IntegrationTestBase {

  @TestConfiguration(proxyBeanMethods = false)
  static class InMemorySpans {
    @Bean
    InMemorySpanExporter inMemorySpanExporter() {
      return InMemorySpanExporter.create();
    }
  }

  @LocalServerPort int port;
  @Autowired InMemorySpanExporter spans;
  @Autowired MeterRegistry meters;
  @Autowired CreateVenue createVenue;
  @Autowired CreateEvent createEvent;

  @Test
  void anHttpRequestIsOneTraceFromControllerToSql() throws InterruptedException {
    var venue =
        createVenue.createVenue(
            new CreateVenue.Command("Traced Hall", "Leeds", ZoneId.of("Europe/London")));
    var event =
        createEvent.createEvent(
            new CreateEvent.Command(
                "Traced",
                venue.id(),
                Instant.now().plus(Duration.ofDays(9)),
                new SeatMap(
                    List.of(new Section("Floor", List.of(new Row("1", List.of(new Seat("1")))))))));
    spans.reset();

    RestClient.create("http://localhost:" + port)
        .get()
        .uri("/admin/events/{id}", event.id().value())
        .retrieve()
        .toBodilessEntity();

    var server = awaitServerSpan();
    var trace =
        spans.getFinishedSpanItems().stream()
            .filter(span -> span.getTraceId().equals(server.getTraceId()))
            .toList();
    var names = trace.stream().map(SpanData::getName).collect(Collectors.toList());

    var useCase =
        trace.stream()
            .filter(span -> span.getName().equals("GetEvent"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no use-case span in " + names));
    assertThat(useCase.getParentSpanId()).as("use case under HTTP").isEqualTo(server.getSpanId());

    var sql =
        trace.stream()
            .filter(span ->
                span.getAttributes().asMap().keySet().stream()
                    .anyMatch(
                        key -> key.getKey().startsWith("db.") || key.getKey().startsWith("jdbc.")))
            .toList();
    assertThat(sql).as("SQL spans in " + names).isNotEmpty();
    assertThat(sql)
        .as("SQL runs inside the use case (directly or under a connection span)")
        .anySatisfy(span ->
            assertThat(List.of(useCase.getSpanId(), parentOf(trace, span)))
                .contains(span.getParentSpanId()));

    // The same observation also produced a timer: rate, errors and duration per use case.
    assertThat(
            meters
                .get(ObservedUseCases.OBSERVATION_NAME)
                .tag("use.case", "GetEvent")
                .timer()
                .count())
        .isPositive();
  }

  private static String parentOf(List<SpanData> trace, SpanData span) {
    return trace.stream()
        .filter(candidate -> candidate.getSpanId().equals(span.getParentSpanId()))
        .map(SpanData::getParentSpanId)
        .findFirst()
        .orElse("");
  }

  private SpanData awaitServerSpan() throws InterruptedException {
    for (int i = 0; i < 100; i++) {
      var server =
          spans.getFinishedSpanItems().stream()
              .filter(span -> span.getKind() == SpanKind.SERVER)
              .filter(span -> span.getName().contains("/admin/events/{id}"))
              .findFirst();
      if (server.isPresent()) {
        Thread.sleep(200); // let the child spans of the same batch arrive
        return server.get();
      }
      Thread.sleep(50);
    }
    throw new AssertionError(
        "no HTTP server span; got "
            + spans.getFinishedSpanItems().stream().map(SpanData::getName).toList());
  }
}
