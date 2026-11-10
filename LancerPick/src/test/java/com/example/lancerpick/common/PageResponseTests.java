package com.example.lancerpick.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.lancerpick.common.web.PageResponse;
import org.junit.jupiter.api.Test;

class PageResponseTests {

  @Test
  void acceptsOnlyNonNegativePageAndSizeOneToHundred() {
    assertThat(PageResponse.isValidRequest(0, 1)).isTrue();
    assertThat(PageResponse.isValidRequest(Integer.MAX_VALUE, 100)).isTrue();
    assertThat(PageResponse.isValidRequest(-1, 20)).isFalse();
    assertThat(PageResponse.isValidRequest(0, 0)).isFalse();
    assertThat(PageResponse.isValidRequest(0, 101)).isFalse();
    assertThat(PageResponse.isValidRequest(0, -1)).isFalse();
  }

  @Test
  void totalPagesRoundsUpAndFailsBeyondIntRange() {
    assertThat(PageResponse.totalPages(0, 20)).isZero();
    assertThat(PageResponse.totalPages(1, 20)).isEqualTo(1);
    assertThat(PageResponse.totalPages(20, 20)).isEqualTo(1);
    assertThat(PageResponse.totalPages(21, 20)).isEqualTo(2);
    assertThat(PageResponse.totalPages(Integer.MAX_VALUE, 1)).isEqualTo(Integer.MAX_VALUE);
    assertThatThrownBy(() -> PageResponse.totalPages(Integer.MAX_VALUE + 1L, 1))
        .isInstanceOf(ArithmeticException.class);
  }
}
