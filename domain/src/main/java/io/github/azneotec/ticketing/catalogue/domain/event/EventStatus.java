package io.github.azneotec.ticketing.catalogue.domain.event;

import io.github.azneotec.ticketing.catalogue.domain.Guard;
import java.time.Instant;

/**
 * Lifecycle state of an {@link Event}. Sealed: these three records are the only states, so a {@code
 * switch} over a status needs no {@code default} and the compiler flags every switch to update when
 * a state is added. Each state carries only the data that exists in that state (a cancel reason
 * exists only once cancelled), instead of nullable columns on one big class.
 */
public sealed interface EventStatus {

  /** Stable code of each state, for filters and storage. */
  enum Code {
    DRAFT,
    PUBLISHED,
    CANCELLED
  }

  Code code();

  /** Being prepared by the organiser; not visible to buyers. */
  record Draft() implements EventStatus {
    @Override
    public Code code() {
      return Code.DRAFT;
    }
  }

  /** Visible to buyers. */
  record Published(Instant publishedAt) implements EventStatus {
    public Published {
      Guard.notNull(publishedAt, "publishedAt");
    }

    @Override
    public Code code() {
      return Code.PUBLISHED;
    }
  }

  /** Final state. */
  record Cancelled(Instant cancelledAt, String reason) implements EventStatus {
    public static final int MAX_REASON_LENGTH = 500;

    public Cancelled {
      Guard.notNull(cancelledAt, "cancelledAt");
      reason = Guard.notBlank(reason, "cancel reason", MAX_REASON_LENGTH);
    }

    @Override
    public Code code() {
      return Code.CANCELLED;
    }
  }
}
