package com.example.lancerpick.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 관리자 운영 문의(K4) 조회 응답. ID는 JSON 문자열, 시각은 UTC다. 관리자는 주문 당사자가 아니므로 주문 상세 대신 판단에 필요한 요약만
 * 담는다(요구사항·납품물·계약 원문은 넣지 않는다).
 */
public final class AdminInquiryResponses {
    private AdminInquiryResponses() {}

    public record Party(String id, String displayName) {}

    public record Summary(
            String id,
            String type,
            String status,
            String subject,
            String orderId,
            String cancellationRequestId,
            Party requester,
            OffsetDateTime createdAt,
            OffsetDateTime resolvedAt) {}

    public record OrderSummary(
            String id,
            String status,
            String policyVersion,
            long priceAmount,
            OffsetDateTime paidAt,
            Party buyer,
            Party seller) {}

    public record CancellationSummary(
            String id,
            String status,
            String reason,
            OffsetDateTime requestedAt,
            OffsetDateTime resolvedAt,
            String resolutionSource,
            String responseReason) {}

    /**
     * @param allowedActions 지금 가능한 관리자 행동. APPROVE·REJECT(취소 검토 문의이고 연결된 요청이 대기 중), CLOSE(열린 문의인데
     *     승인·거절할 대기 요청이 없음). 서버가 처리할 때 다시 검사하므로 화면 표시용이다.
     */
    public record Detail(
            String id,
            String type,
            String status,
            String subject,
            Party requester,
            String assignedAdminId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime resolvedAt,
            OrderSummary order,
            CancellationSummary cancellationRequest,
            List<String> allowedActions) {
        public Detail {
            allowedActions = List.copyOf(allowedActions);
        }
    }
}
