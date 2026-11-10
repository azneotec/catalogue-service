package io.github.azneotec.ticketing.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.azneotec.ticketing.catalogue.application.port.in.CreateEvent;
import io.github.azneotec.ticketing.catalogue.application.port.in.CreateVenue;
import io.github.azneotec.ticketing.catalogue.application.port.in.PublishEvent;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Row;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Seat;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.SeatMap;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Section;
import io.github.azneotec.ticketing.catalogue.grpc.v1.CatalogueServiceGrpc;
import io.github.azneotec.ticketing.catalogue.grpc.v1.EventStatus;
import io.github.azneotec.ticketing.catalogue.grpc.v1.GetEventRequest;
import io.github.azneotec.ticketing.catalogue.grpc.v1.ListEventsRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcServerPort;

/** The gRPC server as other services will see it: real network port, real Postgres. */
class GrpcApiTest extends IntegrationTestBase {

  @LocalGrpcServerPort int grpcPort;
  @Autowired CreateVenue createVenue;
  @Autowired CreateEvent createEvent;
  @Autowired PublishEvent publishEvent;

  private ManagedChannel channel;

  @BeforeEach
  void connect() {
    channel = ManagedChannelBuilder.forAddress("localhost", grpcPort).usePlaintext().build();
  }

  @AfterEach
  void disconnect() throws InterruptedException {
    channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
  }

  @Test
  void getEventAndListEventsOverTheNetwork() {
    var venue =
        createVenue.createVenue(
            new CreateVenue.Command("Arena", "Manchester", ZoneId.of("Europe/London")));
    var draft =
        createEvent.createEvent(
            new CreateEvent.Command(
                "gRPC Night",
                venue.id(),
                Instant.now().plus(Duration.ofDays(40)),
                new SeatMap(
                    List.of(new Section("Floor", List.of(new Row("1", List.of(new Seat("1")))))))));
    publishEvent.publishEvent(draft.id(), draft.version());
    var client =
        CatalogueServiceGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS);

    var event =
        client
            .getEvent(GetEventRequest.newBuilder().setEventId(draft.id().toString()).build())
            .getEvent();
    assertThat(event.getTitle()).isEqualTo("gRPC Night");
    assertThat(event.getVenue().getCity()).isEqualTo("Manchester");
    assertThat(event.getStatus()).isEqualTo(EventStatus.EVENT_STATUS_PUBLISHED);

    var list = client.listEvents(ListEventsRequest.newBuilder().setPageSize(100).build());
    assertThat(list.getEventsList()).extracting(e -> e.getTitle()).contains("gRPC Night");

    assertThatThrownBy(() ->
            client.getEvent(
                GetEventRequest.newBuilder().setEventId(UUID.randomUUID().toString()).build()))
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND));
  }

  @Test
  void standardHealthServiceReportsServing() {
    var health =
        HealthGrpc.newBlockingStub(channel)
            .withDeadlineAfter(5, TimeUnit.SECONDS)
            .check(HealthCheckRequest.getDefaultInstance());

    assertThat(health.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
  }
}
