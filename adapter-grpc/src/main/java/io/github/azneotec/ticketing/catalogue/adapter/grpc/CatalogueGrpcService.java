package io.github.azneotec.ticketing.catalogue.adapter.grpc;

import io.github.azneotec.ticketing.catalogue.adapter.grpc.GrpcErrors.InvalidRequestException;
import io.github.azneotec.ticketing.catalogue.application.NotFoundException;
import io.github.azneotec.ticketing.catalogue.application.PageRequest;
import io.github.azneotec.ticketing.catalogue.application.port.in.GetEvent;
import io.github.azneotec.ticketing.catalogue.application.port.in.GetVenue;
import io.github.azneotec.ticketing.catalogue.application.port.in.ListEvents;
import io.github.azneotec.ticketing.catalogue.domain.event.EventId;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus.Code;
import io.github.azneotec.ticketing.catalogue.grpc.v1.CatalogueServiceGrpc;
import io.github.azneotec.ticketing.catalogue.grpc.v1.GetEventRequest;
import io.github.azneotec.ticketing.catalogue.grpc.v1.GetEventResponse;
import io.github.azneotec.ticketing.catalogue.grpc.v1.ListEventsRequest;
import io.github.azneotec.ticketing.catalogue.grpc.v1.ListEventsResponse;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@code ticketing.catalogue.v1.CatalogueService}: read access to events for other services. A
 * Spring bean of a gRPC service type is all Spring Boot needs to serve it.
 *
 * <p>Deadlines: a client sets one per call and gRPC cancels the call's {@link Context} when it
 * passes. The service checks before starting work and before answering, so a call nobody is waiting
 * for does no database work and sends nothing.
 */
@Component
class CatalogueGrpcService extends CatalogueServiceGrpc.CatalogueServiceImplBase {

  static final int DEFAULT_PAGE_SIZE = 20;
  private static final Logger log = LoggerFactory.getLogger(CatalogueGrpcService.class);

  private final GetEvent getEvent;
  private final GetVenue getVenue;
  private final ListEvents listEvents;

  CatalogueGrpcService(GetEvent getEvent, GetVenue getVenue, ListEvents listEvents) {
    this.getEvent = getEvent;
    this.getVenue = getVenue;
    this.listEvents = listEvents;
  }

  /** Any event, drafts included: callers are trusted services, not buyers. */
  @Override
  public void getEvent(GetEventRequest request, StreamObserver<GetEventResponse> response) {
    respond(
        response,
        () -> {
          var id = eventId(request.getEventId());
          var event = getEvent.getEvent(id).orElseThrow(() -> new NotFoundException("event", id));
          var venue =
              getVenue
                  .getVenue(event.venueId())
                  .orElseThrow(() -> new NotFoundException("venue", event.venueId()));
          return GetEventResponse.newBuilder().setEvent(ProtoMapper.event(event, venue)).build();
        });
  }

  /** Published events, soonest first, one page per call. */
  @Override
  public void listEvents(ListEventsRequest request, StreamObserver<ListEventsResponse> response) {
    respond(
        response,
        () -> {
          var size = pageSize(request.getPageSize());
          var page = PageTokens.page(request.getPageToken());
          var result =
              listEvents.listEvents(
                  new ListEvents.Query(Set.of(Code.PUBLISHED), new PageRequest(page, size)));
          var builder = ListEventsResponse.newBuilder();
          result.items().forEach(summary -> builder.addEvents(ProtoMapper.summary(summary)));
          if (page + 1 < result.totalPages()) {
            builder.setNextPageToken(PageTokens.of(page + 1));
          }
          return builder.build();
        });
  }

  /** Runs one unary call: deadline checks, error mapping, exactly one onNext/onCompleted. */
  private static <T> void respond(StreamObserver<T> response, Supplier<T> work) {
    var context = Context.current();
    if (context.isCancelled()) {
      response.onError(cancelled(context));
      return;
    }
    T result;
    try {
      result = work.get();
    } catch (RuntimeException error) {
      var status = GrpcErrors.toStatus(error);
      if (status.getCode() == Status.Code.INTERNAL) {
        log.error("gRPC call failed", error);
      }
      response.onError(status.asRuntimeException());
      return;
    }
    if (context.isCancelled()) {
      // The client gave up (deadline or cancel) while we were working; the reply would be dropped.
      response.onError(cancelled(context));
      return;
    }
    response.onNext(result);
    response.onCompleted();
  }

  private static RuntimeException cancelled(Context context) {
    var deadline = context.getDeadline();
    var status =
        deadline != null && deadline.isExpired() ? Status.DEADLINE_EXCEEDED : Status.CANCELLED;
    return status.withDescription("client deadline passed or call cancelled").asRuntimeException();
  }

  private static EventId eventId(String value) {
    try {
      return new EventId(UUID.fromString(value));
    } catch (IllegalArgumentException malformed) {
      throw new InvalidRequestException("event_id must be a UUID, got '" + value + "'");
    }
  }

  private static int pageSize(int requested) {
    if (requested < 0) {
      throw new InvalidRequestException("page_size must not be negative");
    }
    // 0 means "server default"; larger requests are capped, as the contract allows.
    return requested == 0 ? DEFAULT_PAGE_SIZE : Math.min(requested, PageRequest.MAX_SIZE);
  }
}
