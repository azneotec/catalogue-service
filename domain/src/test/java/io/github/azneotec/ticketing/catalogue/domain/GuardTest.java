package io.github.azneotec.ticketing.catalogue.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class GuardTest {

  @Test
  void notNullReturnsValueOrFails() {
    assertThat(Guard.notNull("x", "field")).isEqualTo("x");
    assertThatThrownBy(() -> Guard.notNull(null, "field"))
        .isInstanceOf(InvalidValueException.class)
        .hasMessage("field must not be null");
  }

  @Test
  void notBlankStripsAndChecksLength() {
    assertThat(Guard.notBlank("  Wembley ", "name", 10)).isEqualTo("Wembley");
    assertThatThrownBy(() -> Guard.notBlank("   ", "name", 10))
        .isInstanceOf(InvalidValueException.class)
        .hasMessage("name must not be blank");
    assertThatThrownBy(() -> Guard.notBlank("12345678901", "name", 10))
        .hasMessage("name must be at most 10 characters");
    assertThatThrownBy(() -> Guard.notBlank(null, "name", 10)).hasMessage("name must not be null");
  }

  @Test
  void nonEmptyUniqueReturnsAnImmutableCopy() {
    var source = new ArrayList<>(List.of("a", "b"));
    var copy = Guard.nonEmptyUnique(source, Function.identity(), "letters");
    source.add("c");
    assertThat(copy).containsExactly("a", "b");
    assertThatThrownBy(() -> copy.add("d")).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void nonEmptyUniqueRejectsEmptyDuplicateAndNullItems() {
    assertThatThrownBy(() -> Guard.nonEmptyUnique(List.<String>of(), s -> s, "letters"))
        .hasMessage("letters must not be empty");
    assertThatThrownBy(() -> Guard.nonEmptyUnique(List.of("a", "a"), s -> s, "letters"))
        .hasMessage("duplicate letters entry 'a'");
    assertThatThrownBy(() -> Guard.nonEmptyUnique(Arrays.asList("a", null), s -> s, "letters"))
        .hasMessage("letters item must not be null");
    assertThatThrownBy(() -> Guard.<String>nonEmptyUnique(null, s -> s, "letters"))
        .hasMessage("letters must not be null");
  }

  @Test
  void domainExceptionsAreSealedToTwoKinds() {
    DomainException error = new IllegalTransitionException("nope");
    String kind =
        switch (error) {
          case InvalidValueException _ -> "invalid";
          case IllegalTransitionException _ -> "transition";
        };
    assertThat(kind).isEqualTo("transition");
  }
}
