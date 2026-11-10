package io.github.azneotec.ticketing.catalogue.adapter.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.azneotec.ticketing.catalogue.application.ConcurrentUpdateException;
import io.github.azneotec.ticketing.catalogue.application.NotFoundException;
import io.github.azneotec.ticketing.catalogue.domain.IllegalTransitionException;
import io.github.azneotec.ticketing.catalogue.domain.InvalidValueException;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

class GrpcErrorsTest {

  @Test
  void mapsEveryKnownExceptionToItsStatus() {
    assertThat(GrpcErrors.toStatus(new InvalidValueException("bad")).getCode())
        .isEqualTo(Status.Code.INVALID_ARGUMENT);
    assertThat(GrpcErrors.toStatus(new IllegalTransitionException("no")).getCode())
        .isEqualTo(Status.Code.FAILED_PRECONDITION);
    assertThat(GrpcErrors.toStatus(new NotFoundException("event", "1")).getCode())
        .isEqualTo(Status.Code.NOT_FOUND);
    assertThat(GrpcErrors.toStatus(new ConcurrentUpdateException("event", "1")).getCode())
        .isEqualTo(Status.Code.ABORTED);
    assertThat(GrpcErrors.toStatus(new IllegalStateException("boom")).getCode())
        .isEqualTo(Status.Code.INTERNAL);
  }

  @Test
  void keepsTheMessageForClientErrorsOnly() {
    assertThat(GrpcErrors.toStatus(new InvalidValueException("title is blank")).getDescription())
        .isEqualTo("title is blank");
    assertThat(GrpcErrors.toStatus(new IllegalStateException("secret")).getDescription())
        .isEqualTo("internal error");
  }
}
