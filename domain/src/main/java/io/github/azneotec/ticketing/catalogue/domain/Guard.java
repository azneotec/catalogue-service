package io.github.azneotec.ticketing.catalogue.domain;

import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

/**
 * Small validation helpers used by compact constructors. Every failure is an InvalidValueException.
 */
public final class Guard {

  private Guard() {}

  public static <T> T notNull(T value, String field) {
    if (value == null) {
      throw new InvalidValueException(field + " must not be null");
    }
    return value;
  }

  /** Returns the value stripped of surrounding whitespace; fails if blank or too long. */
  public static String notBlank(String value, String field, int maxLength) {
    String stripped = notNull(value, field).strip();
    if (stripped.isEmpty()) {
      throw new InvalidValueException(field + " must not be blank");
    }
    if (stripped.length() > maxLength) {
      throw new InvalidValueException(field + " must be at most " + maxLength + " characters");
    }
    return stripped;
  }

  /**
   * Returns an unmodifiable copy of a non-empty list whose items have unique keys. The copy
   * matters: a record holding the caller's own list would not be immutable.
   */
  public static <T> List<T> nonEmptyUnique(
      List<T> items, Function<? super T, String> key, String what) {
    notNull(items, what);
    if (items.isEmpty()) {
      throw new InvalidValueException(what + " must not be empty");
    }
    var seen = new HashSet<String>();
    for (T item : items) {
      notNull(item, what + " item");
      String k = key.apply(item);
      if (!seen.add(k)) {
        throw new InvalidValueException("duplicate " + what + " entry '" + k + "'");
      }
    }
    return List.copyOf(items);
  }
}
