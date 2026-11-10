package com.example.lancerpick.admin.dto;

import java.time.OffsetDateTime;

/**
 * 관리자 운영 현황(K3). 관리자가 지금 확인할 일의 건수만 모은다. 각 숫자는 조회 순간의 값이며
 * 서로 다른 순간에 센 값일 수 있다(한 트랜잭션 스냅샷을 보장하지 않음). checkedAt은 UTC.
 */
public record AdminDashboardResponse(
        Inquiries inquiries,
        Timers reviewTimers,
        Notifications notifications,
        Topups topups,
        OffsetDateTime checkedAt) {

    /** 운영 문의: 접수 대기(OPEN)·검토 중(IN_REVIEW). */
    public record Inquiries(long open, long inReview) {}

    /** 자동 구매 확정 타이머: 진행 중 전체와 24시간 안에 만료되는 것(이미 만료돼 처리 전인 것 포함). */
    public record Timers(long active, long expiringWithin24Hours) {}

    /** 알림 이벤트: 처리 중단(FAILED)·실패 후 재시도 대기. */
    public record Notifications(long failed, long retrying) {}

    /** 충전: 결제 결과 확인이 필요한 것(UNKNOWN). */
    public record Topups(long unknown) {}
}
