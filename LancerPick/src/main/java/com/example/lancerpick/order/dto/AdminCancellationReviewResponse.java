package com.example.lancerpick.order.dto;

import java.time.OffsetDateTime;

/**
 * 관리자 취소 검토(K4) 처리 결과. 관리자는 주문 당사자가 아니므로 주문 상세 대신 처리 대상 ID와 결과 상태만 돌려준다. ID는 JSON
 * 문자열, 시각은 UTC다.
 *
 * @param decision 취소 요청 처리 결과(APPROVED·REJECTED)
 * @param orderStatus 처리 후 주문 상태(승인 시 CANCELLED, 거절 시 기존 진행 상태)
 * @param inquiryStatus 처리 후 문의 상태(항상 CLOSED)
 */
public record AdminCancellationReviewResponse(
    String inquiryId,
    String orderId,
    String cancellationRequestId,
    String decision,
    String orderStatus,
    String inquiryStatus,
    OffsetDateTime resolvedAt) {}
