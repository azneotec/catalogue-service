package io.github.azneotec.ticketing.catalogue.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.azneotec.ticketing.catalogue.adapter.persistence.SeatMapDocument.RowDocument;
import io.github.azneotec.ticketing.catalogue.adapter.persistence.SeatMapDocument.SectionDocument;
import io.github.azneotec.ticketing.catalogue.domain.InvalidValueException;
import java.util.List;
import org.junit.jupiter.api.Test;

class MapperTest {

  @Test
  void corruptJsonFromTheDatabaseStillFailsDomainValidation() {
    var duplicateSeats =
        new SeatMapDocument(
            List.of(
                new SectionDocument("Stalls", List.of(new RowDocument("A", List.of("1", "1"))))));
    assertThatThrownBy(() -> EventMapper.toSeatMap(duplicateSeats))
        .isInstanceOf(InvalidValueException.class);
  }
}
