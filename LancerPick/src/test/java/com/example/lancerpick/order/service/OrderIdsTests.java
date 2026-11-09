package com.example.lancerpick.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalLong;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class OrderIdsTests {
  @ParameterizedTest
  @ValueSource(strings = {"1", "42", "007", "9223372036854775807"})
  void acceptsPositiveAsciiLong(String raw) {
    assertThat(OrderIds.positive(raw)).isEqualTo(OptionalLong.of(Long.parseLong(raw)));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {"", "0", "000", "-1", "+1", " 1", "1 ", "1.0", "abc", "١٢", "9223372036854775808"})
  void rejectsEverythingElse(String raw) {
    assertThat(OrderIds.positive(raw)).isEmpty();
  }
}
