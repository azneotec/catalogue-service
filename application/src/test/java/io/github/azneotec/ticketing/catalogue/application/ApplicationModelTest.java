package io.github.azneotec.ticketing.catalogue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.azneotec.ticketing.catalogue.application.port.in.ListEvents;
import io.github.azneotec.ticketing.catalogue.domain.event.EventStatus.Code;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApplicationModelTest {

  @Test
  void pageRequestValidatesAndComputesOffset() {
    assertThat(new PageRequest(3, 20).offset()).isEqualTo(60);
    assertThatThrownBy(() -> new PageRequest(-1, 10)).hasMessage("page must be >= 0");
    assertThatThrownBy(() -> new PageRequest(0, 0)).hasMessage("size must be between 1 and 100");
    assertThatThrownBy(() -> new PageRequest(0, 101)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void pageCountsPagesAndCopiesItems() {
    var items = new ArrayList<>(List.of("a", "b"));
    var page = new Page<>(items, new PageRequest(0, 2), 5);
    items.add("c");
    assertThat(page.items()).containsExactly("a", "b");
    assertThat(page.totalPages()).isEqualTo(3);
    assertThat(new Page<>(List.of(), new PageRequest(0, 2), 0).totalPages()).isZero();
  }

  @Test
  void listQueryNeedsStatusesAndAPage() {
    var page = new PageRequest(0, 10);
    assertThatThrownBy(() -> new ListEvents.Query(Set.of(), page))
        .hasMessage("statuses must not be empty");
    assertThatThrownBy(() -> new ListEvents.Query(null, page))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ListEvents.Query(Set.of(Code.DRAFT), null))
        .hasMessage("page must not be null");
  }

  @Test
  void exceptionsExplainThemselves() {
    assertThat(new NotFoundException("event", "42")).hasMessage("event 42 not found");
    var cause = new RuntimeException("db");
    assertThat(new ConcurrentUpdateException("event", "42", cause)).hasCause(cause);
  }
}
