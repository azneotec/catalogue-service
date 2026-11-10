package io.github.azneotec.ticketing.catalogue;

import io.github.azneotec.ticketing.archrules.HexagonalArchitecture;
import org.junit.jupiter.api.Test;

/** Strict hexagonal layering, checked on the compiled classes of every module (ADR-004). */
class ArchitectureTest {

  @Test
  void isHexagonal() {
    HexagonalArchitecture.forBasePackage("io.github.azneotec.ticketing.catalogue").check();
  }
}
