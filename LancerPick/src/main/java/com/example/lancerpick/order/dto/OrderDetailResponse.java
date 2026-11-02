package com.example.lancerpick.order.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 공개 주문 계약. 내부 요청 키·이메일·결제 제공자 키는 포함하지 않는다. */
public record OrderDetailResponse(
        String id, String status, Participant buyer, Participant seller, String productId,
        ProductSnapshot productSnapshot, String termsSnapshot, long priceAmount, int durationDays,
        String requirements, OffsetDateTime createdAt, OffsetDateTime acceptedAt,
        OffsetDateTime buyerConfirmedTermsAt, OffsetDateTime paidAt, OffsetDateTime originalDueAt,
        OffsetDateTime revisionDueAt, OffsetDateTime currentDueAt, OffsetDateTime completedAt,
        OffsetDateTime cancelledAt, List<Delivery> deliveries, RevisionRequest revisionRequest,
        List<CancellationRequest> cancellationRequests, List<History> histories,
        Payment payment, Review review, List<String> allowedActions) {

    public OrderDetailResponse withAllowedActions(List<String> actions) {
        return new OrderDetailResponse(id, status, buyer, seller, productId, productSnapshot, termsSnapshot,
                priceAmount, durationDays, requirements, createdAt, acceptedAt, buyerConfirmedTermsAt,
                paidAt, originalDueAt, revisionDueAt, currentDueAt, completedAt, cancelledAt, deliveries,
                revisionRequest, cancellationRequests, histories, payment, review, List.copyOf(actions));
    }

    public record Participant(String id, String displayName) {}
    public record ProductSnapshot(String title, String description, String scopeDescription,
                                  String preparationDescription, long productVersion) {}
    public record Delivery(String id, int sequenceNo, String description, String resultUrl,
                           OffsetDateTime submittedAt) {}
    public record RevisionRequest(String id, String deliveryId, String reason,
                                  OffsetDateTime requestedAt, OffsetDateTime dueAt) {}
    public record CancellationRequest(String id, String orderId, String requestKey, String reason,
            String status, OffsetDateTime requestedAt, OffsetDateTime resolvedAt,
            String resolvedBy, String responseReason) {}
    public record History(String id, String actorId, String action, String fromStatus,
                          String toStatus, String reason, OffsetDateTime createdAt) {}
    public record Payment(String id, long amount, int feeRateBps, String status,
                          OffsetDateTime paidAt, OffsetDateTime refundedAt) {}
    public record Review(String id, String orderId, String authorDisplayName, int rating,
                         String content, OffsetDateTime createdAt) {}
}
