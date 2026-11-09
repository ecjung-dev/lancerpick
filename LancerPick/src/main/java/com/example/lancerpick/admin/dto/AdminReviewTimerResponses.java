package com.example.lancerpick.admin.dto;

import com.example.lancerpick.admin.dto.AdminInquiryResponses.Party;
import java.time.OffsetDateTime;
import tools.jackson.databind.annotation.JsonDeserialize;

/** 관리자 타이머 해제(K5) 요청·조회 응답. ID는 JSON 문자열, 시각은 UTC다. */
public final class AdminReviewTimerResponses {
    private AdminReviewTimerResponses() {}

    /** 진행 중(ACTIVE)인 검수 무응답 자동 구매 확정 타이머. 만료 시각이 이른 순으로 보여 준다. */
    public record ActiveTimer(
            String timerId,
            String orderId,
            int cycleNo,
            OffsetDateTime startedAt,
            OffsetDateTime expiresAt,
            Party buyer,
            Party seller) {}

    /** 해제 사유. 공백 정리·1~1000자 검사는 ReviewTimerService.adminStop이 한다. */
    public record StopRequest(@JsonDeserialize(using = AdminRequestJson.StringValue.class) String reason) {}
}
