package com.example.lancerpick.member.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Calendar months in Korea, persisted as UTC. Month-end dates clamp to the last valid day. */
public final class MemberChangePolicy {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private MemberChangePolicy() {}

    public static LocalDateTime sixMonthsAfter(LocalDateTime utc) {
        return sixMonthPeriodsAfter(utc, 1);
    }

    /**
     * 기준 시각에서 6개월 × periods 뒤. 앞 회차 결과에 6개월을 다시 더하지 않고 항상 기준에서 계산한다
     * (8/31 기준 2회차는 2/28 + 6개월 = 8/28이 아니라 8/31).
     */
    public static LocalDateTime sixMonthPeriodsAfter(LocalDateTime utc, long periods) {
        if (periods < 0) throw new IllegalArgumentException("Invalid period count");
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KOREA)
                .plusMonths(6 * periods).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** now까지 다 지난 6개월 구간 수. now가 기준 + 6개월 전이면 0이다. */
    public static long elapsedSixMonthPeriods(LocalDateTime baseUtc, LocalDateTime nowUtc) {
        if (nowUtc.isBefore(baseUtc)) return 0;
        long months = java.time.temporal.ChronoUnit.MONTHS.between(
                baseUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KOREA),
                nowUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KOREA));
        long periods = Math.max(0, months / 6);
        // 월말 보정으로 경계가 하루 이상 당겨질 수 있어 실제 경계와 비교해 맞춘다.
        while (!sixMonthPeriodsAfter(baseUtc, periods + 1).isAfter(nowUtc)) periods++;
        while (periods > 0 && sixMonthPeriodsAfter(baseUtc, periods).isAfter(nowUtc)) periods--;
        return periods;
    }
}
