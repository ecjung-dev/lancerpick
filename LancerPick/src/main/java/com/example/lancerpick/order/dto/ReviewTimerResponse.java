package com.example.lancerpick.order.dto;

import java.time.OffsetDateTime;

/**
 * V3 검수 무응답 자동 구매 확정 상태. status: NOT_IN_REVIEW(검수 대기 아님), CANCELLATION_PENDING(취소 요청 대기),
 * WAITING(기산점+48시간 전), STARTABLE(판매자가 시작 가능), ADMIN_STOPPED(이 기산점의 타이머를 관리자가 해제),
 * ACTIVE(진행 중), AUTO_CONFIRMED(타이머 만료로 구매 확정됨). 시각은 해당하지 않으면 null이다.
 */
public record ReviewTimerResponse(
    String status,
    OffsetDateTime basisAt,
    OffsetDateTime eligibleAt,
    OffsetDateTime startedAt,
    OffsetDateTime expiresAt,
    boolean canStart) {}
