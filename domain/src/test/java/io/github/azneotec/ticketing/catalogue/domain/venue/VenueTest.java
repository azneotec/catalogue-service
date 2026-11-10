package io.github.azneotec.ticketing.catalogue.domain.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.azneotec.ticketing.catalogue.domain.InvalidValueException;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VenueTest {

  private static final ZoneId LONDON = ZoneId.of("Europe/London");

  @Test
  void keepsNormalisedValues() {
    var id = VenueId.newId();
    var venue = new Venue(id, " Royal Albert Hall ", "London", LONDON);
    assertThat(venue.id()).isEqualTo(id);
    assertThat(venue.name()).isEqualTo("Royal Albert Hall");
    assertThat(venue.city()).isEqualTo("London");
    assertThat(venue.zone()).isEqualTo(LONDON);
    assertThat(venue).hasToString("Venue[" + id + ", Royal Albert Hall, London]");
  }

  @Test
  void isAnEntityEqualByIdOnly() {
    var id = VenueId.newId();
    var a = new Venue(id, "Old name", "London", LONDON);
    var b = new Venue(id, "New name", "Paris", ZoneId.of("Europe/Paris"));
    var c = new Venue(VenueId.newId(), "Old name", "London", LONDON);
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(c).isNotEqualTo("Old name");
  }

  @Test
  void rejectsInvalidValues() {
    var id = VenueId.newId();
    assertThatThrownBy(() -> new Venue(null, "n", "c", LONDON))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(() -> new Venue(id, " ", "c", LONDON))
        .hasMessage("venue name must not be blank");
    assertThatThrownBy(() -> new Venue(id, "n", "x".repeat(101), LONDON))
        .hasMessage("venue city must be at most 100 characters");
    assertThatThrownBy(() -> new Venue(id, "n", "c", null))
        .hasMessage("venue zone must not be null");
  }

  @Test
  void venueIdWrapsAUuid() {
    var uuid = UUID.randomUUID();
    assertThat(new VenueId(uuid)).isEqualTo(new VenueId(uuid)).hasToString(uuid.toString());
    assertThat(VenueId.newId()).isNotEqualTo(VenueId.newId());
    assertThatThrownBy(() -> new VenueId(null)).isInstanceOf(InvalidValueException.class);
  }
}
