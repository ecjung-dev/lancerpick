package com.example.lancerpick.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.lancerpick.member.service.MemberChangePolicy;
import java.time.LocalDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MemberChangePolicyTests {
    @ParameterizedTest
    @CsvSource({
        "2026-02-28T15:30:00.123456, 2026-08-31T15:30:00.123456",
        "2023-08-30T16:00:00, 2024-02-28T16:00:00",
        "2024-08-30T16:00:00, 2025-02-27T16:00:00"
    })
    void sixCalendarMonthsUseKoreanDateAndClampLeapYearMonthEnds(String from, String to) {
        assertThat(MemberChangePolicy.sixMonthsAfter(LocalDateTime.parse(from)))
                .isEqualTo(LocalDateTime.parse(to));
    }

    // 기준 2026-08-31 12:00 KST(03:00 UTC). 회차 경계는 항상 기준에서 6개월 × n(월말 보정)으로 계산한다.
    @ParameterizedTest
    @CsvSource({
        "2026-08-31T02:59:59, 0",
        "2027-02-28T02:59:59.999999, 0",
        "2027-02-28T03:00:00, 1",
        "2027-08-30T03:00:00, 1",
        "2027-08-31T02:59:59.999999, 1",
        "2027-08-31T03:00:00, 2",
        "2029-03-03T00:00:00, 5"
    })
    void elapsedPeriodsCountFromTheBasisNotFromThePreviousBoundary(String now, long expected) {
        var basis = LocalDateTime.parse("2026-08-31T03:00:00");
        assertThat(MemberChangePolicy.elapsedSixMonthPeriods(basis, LocalDateTime.parse(now)))
                .isEqualTo(expected);
    }
}
