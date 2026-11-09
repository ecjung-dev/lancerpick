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
}
