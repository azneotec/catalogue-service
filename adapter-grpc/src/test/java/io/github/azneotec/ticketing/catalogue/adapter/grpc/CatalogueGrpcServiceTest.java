package io.github.azneotec.ticketing.catalogue.adapter.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.azneotec.ticketing.catalogue.application.EventSummary;
import io.github.azneotec.ticketing.catalogue.application.Page;
import io.github.azneotec.ticketing.catalogue.application.PageRequest;
import io.github.azneotec.ticketing.catalogue.application.port.in.ListEvents;
import io.github.azneotec.ticketing.catalogue.domain.event.Event;
import io.github.azneotec.ticketing.catalogue.domain.event.EventId;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus.Code;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Row;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Seat;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.SeatMap;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Section;
import io.github.azneotec.ticketing.catalogue.domain.venue.Venue;
import io.github.azneotec.ticketing.catalogue.domain.venue.VenueId;
import io.github.azneotec.ticketing.catalogue.grpc.v1.CatalogueServiceGrpc;
import io.github.azneotec.ticketing.catalogue.grpc.v1.CatalogueServiceGrpc.CatalogueServiceBlockingStub;
import io.github.azneotec.ticketing.catalogue.grpc.v1.GetEventRequest;
import io.github.azneotec.ticketing.catalogue.grpc.v1.ListEventsRequest;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The gRPC adapter through a real gRPC client and server that talk in memory (in-process
 * transport): real serialisation, status codes and deadlines, no network and no Spring. The use
 * cases are lambdas over maps.
 */
class CatalogueGrpcServiceTest {

  private static final ZoneId LONDON = ZoneId.of("Europe/London");
  private static final Instant STARTS_AT = Instant.parse("2026-12-01T19:30:00.250Z");
  private static final Venue VENUE = new Venue(VenueId.newId(), "Hall", "London", LONDON);
  private static final SeatMap SEAT_MAP =
      new SeatMap(List.of(new Section("Stalls", List.of(new Row("A", List.of(new Seat("1")))))));

  private final Map<EventId, Event> events = new HashMap<>();
  private final Map<VenueId, Venue> venues = new HashMap<>(Map.of(VENUE.id(), VENUE));
  private final List<ListEvents.Query> queries = new ArrayList<>();
  private long publishedTotal = 0;
  private RuntimeException failure;
  private CountDownLatch slowGetEvent;

  private final CatalogueGrpcService service =
      new CatalogueGrpcService(
          id -> {
            if (failure != null) {
              throw failure;
            }
            awaitIfSlow();
            return Optional.ofNullable(events.get(id));
          },
          id -> Optional.ofNullable(venues.get(id)),
          query -> {
            queries.add(query);
            var summary =
                new EventSummary(
                    EventId.newId(),
                    "Concert",
                    STARTS_AT,
                    LONDON,
                    Code.PUBLISHED,
                    "Hall",
                    "London");
            return new Page<>(List.of(summary), query.page(), publishedTotal);
          });

  private Server server;
  private ManagedChannel channel;
  private CatalogueServiceBlockingStub client;

  @BeforeEach
  void start() throws Exception {
    var name = InProcessServerBuilder.generateName();
    server = InProcessServerBuilder.forName(name).directExecutor().addService(service).build();
    server.start();
    channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    client = CatalogueServiceGrpc.newBlockingStub(channel);
  }

  @AfterEach
  void stop() {
    if (slowGetEvent != null) {
      slowGetEvent.countDown();
    }
    channel.shutdownNow();
    server.shutdownNow();
  }

  private void awaitIfSlow() {
    if (slowGetEvent != null) {
      try {
        slowGetEvent.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private Event storeEvent(EventStatus status) {
    var event =
        Event.restore(
            EventId.newId(), "Concert", VENUE.id(), STARTS_AT, LONDON, SEAT_MAP, status, 1);
    events.put(event.id(), event);
    return event;
  }

  private static Status.Code codeOf(Runnable call) {
    try {
      call.run();
    } catch (StatusRuntimeException e) {
      return e.getStatus().getCode();
    }
    throw new AssertionError("expected the call to fail");
  }

  @Test
  void getEventReturnsTheEventWithItsVenueAndSchedule() {
    var stored = storeEvent(new EventStatus.Published(Instant.parse("2026-11-01T00:00:00Z")));

    var event =
        client
            .getEvent(GetEventRequest.newBuilder().setEventId(stored.id().toString()).build())
            .getEvent();

    assertThat(event.getEventId()).isEqualTo(stored.id().toString());
    assertThat(event.getTitle()).isEqualTo("Concert");
    assertThat(event.getVenue().getVenueId()).isEqualTo(VENUE.id().toString());
    assertThat(event.getVenue().getName()).isEqualTo("Hall");
    assertThat(event.getVenue().getCity()).isEqualTo("London");
    assertThat(event.getSchedule().getStartsAt().getSeconds())
        .isEqualTo(STARTS_AT.getEpochSecond());
    assertThat(event.getSchedule().getStartsAt().getNanos()).isEqualTo(250_000_000);
    assertThat(event.getSchedule().getZoneId()).isEqualTo("Europe/London");
    assertThat(event.getSchedule().hasEndsAt()).isFalse();
    assertThat(event.getStatus())
        .isEqualTo(
            io.github.azneotec.ticketing.catalogue.grpc.v1.EventStatus.EVENT_STATUS_PUBLISHED);
  }

  @Test
  void getEventReturnsDraftsAndCancelledEventsToo() {
    var draft = storeEvent(new EventStatus.Draft());
    var cancelled = storeEvent(new EventStatus.Cancelled(STARTS_AT, "Storm"));

    assertThat(
            client
                .getEvent(GetEventRequest.newBuilder().setEventId(draft.id().toString()).build())
                .getEvent()
                .getStatus())
        .isEqualTo(io.github.azneotec.ticketing.catalogue.grpc.v1.EventStatus.EVENT_STATUS_DRAFT);
    assertThat(
            client
                .getEvent(
                    GetEventRequest.newBuilder().setEventId(cancelled.id().toString()).build())
                .getEvent()
                .getStatus())
        .isEqualTo(
            io.github.azneotec.ticketing.catalogue.grpc.v1.EventStatus.EVENT_STATUS_CANCELLED);
  }

  @Test
  void getEventMapsErrorsToStatusCodes() {
    assertThat(codeOf(() -> client.getEvent(GetEventRequest.newBuilder().setEventId("x").build())))
        .isEqualTo(Status.Code.INVALID_ARGUMENT);
    assertThat(
            codeOf(() ->
                client.getEvent(
                    GetEventRequest.newBuilder().setEventId(UUID.randomUUID().toString()).build())))
        .isEqualTo(Status.Code.NOT_FOUND);

    var orphan = storeEvent(new EventStatus.Draft());
    venues.clear();
    assertThat(
            codeOf(() ->
                client.getEvent(
                    GetEventRequest.newBuilder().setEventId(orphan.id().toString()).build())))
        .isEqualTo(Status.Code.NOT_FOUND);
  }

  @Test
  void unexpectedErrorsBecomeInternalWithoutDetails() {
    failure = new IllegalStateException("database password is hunter2");

    assertThatThrownBy(() ->
            client.getEvent(
                GetEventRequest.newBuilder().setEventId(UUID.randomUUID().toString()).build()))
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            e -> {
              assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
              assertThat(e.getStatus().getDescription()).isEqualTo("internal error");
            });
  }

  @Test
  void listEventsPagesPublishedEventsWithOpaqueTokens() {
    publishedTotal = 45;

    var first = client.listEvents(ListEventsRequest.getDefaultInstance());
    assertThat(first.getEventsList()).hasSize(1);
    assertThat(first.getEvents(0).getVenue().getName()).isEqualTo("Hall");
    assertThat(first.getNextPageToken()).isNotEmpty().doesNotContain("page");

    var second =
        client.listEvents(
            ListEventsRequest.newBuilder().setPageToken(first.getNextPageToken()).build());
    var third =
        client.listEvents(
            ListEventsRequest.newBuilder().setPageToken(second.getNextPageToken()).build());
    assertThat(third.getNextPageToken()).as("last page").isEmpty();

    assertThat(queries)
        .extracting(ListEvents.Query::page)
        .containsExactly(new PageRequest(0, 20), new PageRequest(1, 20), new PageRequest(2, 20));
    assertThat(queries).allSatisfy(q -> assertThat(q.statuses()).containsExactly(Code.PUBLISHED));
  }

  @Test
  void listEventsCapsThePageSizeAndRejectsBadInput() {
    client.listEvents(ListEventsRequest.newBuilder().setPageSize(500).build());
    assertThat(queries.getLast().page().size()).isEqualTo(PageRequest.MAX_SIZE);

    assertThat(
            codeOf(() -> client.listEvents(ListEventsRequest.newBuilder().setPageSize(-1).build())))
        .isEqualTo(Status.Code.INVALID_ARGUMENT);
    for (var token : List.of("!!!", PageTokens.of(0).substring(1), "cGFnZTot", "eHl6")) {
      assertThat(
              codeOf(() ->
                  client.listEvents(ListEventsRequest.newBuilder().setPageToken(token).build())))
          .as(token)
          .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }
  }

  @Test
  void aClientDeadlineEndsTheCallWithDeadlineExceeded() {
    var stored = storeEvent(new EventStatus.Draft());
    slowGetEvent = new CountDownLatch(1);
    // A real executor this time, so the client's deadline timer runs while the server is busy.
    var name = InProcessServerBuilder.generateName();
    try {
      var slowServer = InProcessServerBuilder.forName(name).addService(service).build().start();
      var slowChannel = InProcessChannelBuilder.forName(name).build();
      try {
        var started = System.nanoTime();
        assertThat(
                codeOf(() ->
                    CatalogueServiceGrpc.newBlockingStub(slowChannel)
                        .withDeadlineAfter(100, TimeUnit.MILLISECONDS)
                        .getEvent(
                            GetEventRequest.newBuilder()
                                .setEventId(stored.id().toString())
                                .build())))
            .isEqualTo(Status.Code.DEADLINE_EXCEEDED);
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(2000);
      } finally {
        slowChannel.shutdownNow();
        slowServer.shutdownNow();
      }
    } catch (java.io.IOException e) {
      throw new AssertionError(e);
    }
  }
}
