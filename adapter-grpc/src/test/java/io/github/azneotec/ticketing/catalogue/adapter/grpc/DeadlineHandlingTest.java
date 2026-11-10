package io.github.azneotec.ticketing.catalogue.adapter.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.azneotec.ticketing.catalogue.domain.event.Event;
import io.github.azneotec.ticketing.catalogue.domain.event.EventId;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Row;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Seat;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.SeatMap;
import io.github.azneotec.ticketing.catalogue.domain.seatmap.Section;
import io.github.azneotec.ticketing.catalogue.domain.venue.Venue;
import io.github.azneotec.ticketing.catalogue.domain.venue.VenueId;
import io.github.azneotec.ticketing.catalogue.grpc.v1.GetEventRequest;
import io.github.azneotec.ticketing.catalogue.grpc.v1.GetEventResponse;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * What the server does with a call whose deadline has passed: it skips the work, or drops a result
 * nobody is waiting for. The gRPC {@link Context} is driven by hand here, to make timing exact.
 */
class DeadlineHandlingTest {

  private static final Venue VENUE =
      new Venue(VenueId.newId(), "Hall", "London", ZoneId.of("Europe/London"));
  private static final Event EVENT =
      Event.create(
          EventId.newId(),
          "Concert",
          VENUE,
          Instant.parse("2026-12-01T19:30:00Z"),
          new SeatMap(
              List.of(new Section("Stalls", List.of(new Row("A", List.of(new Seat("1"))))))));

  private final AtomicInteger useCaseCalls = new AtomicInteger();
  private final AtomicReference<Context.CancellableContext> cancelDuringWork =
      new AtomicReference<>();

  private final CatalogueGrpcService service =
      new CatalogueGrpcService(
          id -> {
            useCaseCalls.incrementAndGet();
            var context = cancelDuringWork.get();
            if (context != null) {
              context.cancel(null); // the client gives up while we are querying
            }
            return Optional.of(EVENT);
          },
          id -> Optional.of(VENUE),
          query -> {
            throw new AssertionError("not used");
          });

  private static final GetEventRequest REQUEST =
      GetEventRequest.newBuilder().setEventId(UUID.randomUUID().toString()).build();

  @Test
  void anExpiredDeadlineSkipsTheWork() {
    var scheduler = Executors.newSingleThreadScheduledExecutor();
    try (var context =
        Context.current().withDeadline(Deadline.after(-1, TimeUnit.MILLISECONDS), scheduler)) {
      var observer = new Recorder();

      context.run(() -> service.getEvent(REQUEST, observer));

      assertThat(useCaseCalls).hasValue(0);
      assertThat(observer.errorCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED);
    } finally {
      scheduler.shutdownNow();
    }
  }

  @Test
  void aCallCancelledDuringTheWorkSendsNoResult() {
    try (var context = Context.current().withCancellation()) {
      cancelDuringWork.set(context);
      var observer = new Recorder();

      context.run(() -> service.getEvent(REQUEST, observer));

      assertThat(useCaseCalls).hasValue(1);
      assertThat(observer.values).isEmpty();
      // The event was found, but nobody is listening any more: no result, just CANCELLED.
      assertThat(observer.errorCode()).isEqualTo(Status.Code.CANCELLED);
    }
  }

  private static final class Recorder implements StreamObserver<GetEventResponse> {
    final List<GetEventResponse> values = new ArrayList<>();
    Throwable error;

    @Override
    public void onNext(GetEventResponse value) {
      values.add(value);
    }

    @Override
    public void onError(Throwable t) {
      error = t;
    }

    @Override
    public void onCompleted() {}

    Status.Code errorCode() {
      return Status.fromThrowable(error).getCode();
    }
  }
}
