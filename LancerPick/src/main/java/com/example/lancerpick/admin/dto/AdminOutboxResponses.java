package com.example.lancerpick.admin.dto;

import java.time.OffsetDateTime;

/** 관리자 알림 처리 실패 조회(K6). ID는 JSON 문자열, 시각은 UTC다. 페이로드(수신자 등)는 응답하지 않는다. */
public final class AdminOutboxResponses {
    private AdminOutboxResponses() {}

    /**
     * 처리되지 않은 알림 이벤트 한 건.
     * status FAILED: 자동 재시도를 멈춤. RETRYING: 실패 후 nextAttemptAt에 다시 시도할 예정(PENDING이며 시도 1회 이상).
     * lastErrorCode는 고정 코드(event-contract.md)이며, 예외 원문은 저장·응답하지 않는다.
     */
    public record OutboxIssue(
            String id,
            String eventId,
            String type,
            String aggregateType,
            String aggregateId,
            String status,
            int attemptCount,
            String lastErrorCode,
            OffsetDateTime nextAttemptAt,
            OffsetDateTime createdAt) {}
}
