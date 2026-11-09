package com.example.lancerpick.order.service;

import java.util.OptionalLong;

/** 주문 API의 문자열 ID를 양의 long으로 해석한다. 오류 응답은 호출자마다 다르므로 빈 값만 돌려준다. */
final class OrderIds {
  private OrderIds() {}

  /** ASCII 숫자만, long 범위, 1 이상일 때 값을 돌려준다. 그 밖(null·부호·공백·오버플로·0)은 빈 값이다. */
  static OptionalLong positive(String raw) {
    if (raw == null || !raw.matches("[0-9]+")) return OptionalLong.empty();
    try {
      long value = Long.parseLong(raw);
      return value > 0 ? OptionalLong.of(value) : OptionalLong.empty();
    } catch (NumberFormatException e) {
      return OptionalLong.empty();
    }
  }
}
