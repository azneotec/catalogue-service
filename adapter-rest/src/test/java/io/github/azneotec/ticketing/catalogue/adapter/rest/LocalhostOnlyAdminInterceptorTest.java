package io.github.azneotec.ticketing.catalogue.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class LocalhostOnlyAdminInterceptorTest {

  @ParameterizedTest
  @ValueSource(strings = {"127.0.0.1", "127.8.9.10", "::1", "0:0:0:0:0:0:0:1"})
  void loopbackAddressesAreLocal(String address) {
    assertThat(LocalhostOnlyAdminInterceptor.isLoopback(address)).isTrue();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"10.0.0.1", "203.0.113.7", "fe80::1", "localhost", "", "127.0.0.1.evil"})
  void everythingElseIsNot(String address) {
    assertThat(LocalhostOnlyAdminInterceptor.isLoopback(address)).isFalse();
  }
}
