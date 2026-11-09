package com.example.lancerpick.member.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Calendar months in Korea, persisted as UTC. Month-end dates clamp to the last valid day. */
public final class MemberChangePolicy {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private MemberChangePolicy() {}

    public static LocalDateTime sixMonthsAfter(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KOREA)
                .plusMonths(6).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
