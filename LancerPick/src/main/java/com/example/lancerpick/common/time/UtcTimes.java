package com.example.lancerpick.common.time;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** DB에 저장하는 시각 규칙: UTC LocalDateTime, DATETIME(6)에 맞춘 마이크로초 절삭. */
public final class UtcTimes {
  private UtcTimes() {}

  /** 호출한 시점의 시각을 읽는다. 잠금 뒤 시각이 필요한 곳은 잠금 뒤에 호출한다. */
  public static LocalDateTime nowMicros(Clock clock) {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }
}
