package com.example.lancerpick.order.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import com.example.lancerpick.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@lombok.Getter
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "buyer_id", nullable = false, updatable = false)
    private Long buyerId;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private Long sellerId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "request_key", nullable = false, updatable = false,
            columnDefinition = "char(36)")
    private String requestKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "price_amount", nullable = false, updatable = false)
    private Long priceAmount;

    @Column(name = "policy_version", nullable = false, updatable = false, length = 32)
    private String policyVersion = com.example.lancerpick.common.policy.TradePolicyVersion.LEGACY_V1.name();

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "inspection_approved_at")
    private LocalDateTime inspectionApprovedAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;

    @Column(name = "chat_sequence", nullable = false)
    private long chatSequence;

    @Column(name = "duration_days_snapshot", nullable = false, updatable = false)
    private Integer durationDaysSnapshot;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "buyer_confirmed_terms_at")
    private LocalDateTime buyerConfirmedTermsAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    protected Order() {
    }

    public static Order create(
            Long buyerId,
            Long sellerId,
            Long productId,
            UUID requestKey,
            Long priceAmount,
            Integer durationDaysSnapshot,
            LocalDateTime now
    ) {
        return create(buyerId, sellerId, productId, requestKey, priceAmount, durationDaysSnapshot, now,
                com.example.lancerpick.common.policy.TradePolicyVersion.LEGACY_V1);
    }

    /** 정책 버전은 생성 시 고정되며 이후 변경하지 않는다. */
    public static Order create(
            Long buyerId,
            Long sellerId,
            Long productId,
            UUID requestKey,
            Long priceAmount,
            Integer durationDaysSnapshot,
            LocalDateTime now,
            com.example.lancerpick.common.policy.TradePolicyVersion policyVersion
    ) {
        if (policyVersion == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "주문 정책 버전이 필요합니다."
            );
        }

        if (buyerId == null || sellerId == null
                || buyerId <= 0 || sellerId <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "구매자와 판매자 ID는 양수여야 합니다."
            );
        }

        if (buyerId.equals(sellerId)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "SELF_PURCHASE_NOT_ALLOWED",
                    "자신의 상품은 주문할 수 없습니다."
            );
        }

        if (priceAmount == null || priceAmount < 1 || priceAmount > 1_000_000) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "주문 가격은 1~1,000,000원이어야 합니다."
            );
        }

        if (durationDaysSnapshot == null
                || durationDaysSnapshot < 1 || durationDaysSnapshot > 30) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "소요일은 1~30일이어야 합니다."
            );
        }

        if (productId == null || productId <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "상품 ID는 양수여야 합니다."
            );
        }

        if (requestKey == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "주문 요청 키가 필요합니다."
            );
        }

        if (now == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "주문 생성 시각이 필요합니다."
            );
        }

        Order order = new Order();

        order.buyerId = buyerId;
        order.sellerId = sellerId;
        order.productId = productId;
        order.requestKey = requestKey.toString();
        order.status = OrderStatus.REQUESTED;
        order.priceAmount = priceAmount;
        order.durationDaysSnapshot = durationDaysSnapshot;
        order.policyVersion = policyVersion.name();
        order.createdAt = now;
        order.updatedAt = now;

        return order;
    }

    public OrderStatus getStatus() {
        return status;
    }

    /** 기존 주문은 결제 대기, 계약 주문은 양측 서명 전까지 계약 대기로 간다. */
    public void accept(LocalDateTime now) {
        requireRequested(now);
        status = isContractPolicy() ? OrderStatus.AWAITING_CONTRACT : OrderStatus.AWAITING_PAYMENT;
        acceptedAt = now.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        updatedAt = acceptedAt;
    }

    /** 현재 계약에 양측 서명이 모두 모이면 선금 대기(AWAITING_PAYMENT)로 전환한다. */
    public void contractSigned(LocalDateTime at) {
        if (!isContractPolicy() || status != OrderStatus.AWAITING_CONTRACT || paidAt != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "ORDER_STATE_CONFLICT", "계약 대기 주문만 계약을 성립할 수 있습니다.");
        }
        if (at == null || at.getYear() < 1000 || at.getYear() > 9999 || at.isBefore(updatedAt)) {
            throw new IllegalStateException("Invalid contract signing clock");
        }
        status = OrderStatus.AWAITING_PAYMENT;
        updatedAt = at.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    public boolean isContractPolicy() {
        return com.example.lancerpick.common.policy.TradePolicyVersion.CONTRACT_V2.name().equals(policyVersion);
    }

    public void reject(LocalDateTime now) {
        requireRequested(now);
        status = OrderStatus.REJECTED;
        updatedAt = now.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    private void requireRequested(LocalDateTime now) {
        if (status != OrderStatus.REQUESTED) {
            throw new BusinessException(HttpStatus.CONFLICT, "ORDER_STATE_CONFLICT", "수락·거절 가능한 주문 상태가 아닙니다.");
        }
        if (now == null || now.getYear() < 1000 || now.getYear() > 9999 || now.isBefore(updatedAt)) {
            throw new IllegalStateException("Invalid order decision clock");
        }
    }

    public Long getSellerId() { return sellerId; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void confirm(LocalDateTime at) {
        if (status != OrderStatus.AWAITING_CONFIRMATION) throw new BusinessException(HttpStatus.CONFLICT,"ORDER_STATE_CONFLICT","검수 대기 주문만 구매 확정할 수 있습니다.");
        if (at == null || at.getYear() < 1000 || at.getYear() > 9999 || at.isBefore(updatedAt)) throw new IllegalStateException("Invalid confirmation clock");
        status = OrderStatus.COMPLETED;
        completedAt = at;
        updatedAt = at;
    }
    public void cancel(LocalDateTime at) {
        if (status == OrderStatus.COMPLETED || status == OrderStatus.REJECTED || status == OrderStatus.CANCELLED) throw new BusinessException(HttpStatus.CONFLICT,"ORDER_STATE_CONFLICT","취소할 수 없는 주문입니다.");
        if (at == null || at.getYear() < 1000 || at.getYear() > 9999 || at.isBefore(updatedAt)) throw new IllegalStateException("Invalid cancellation clock");
        status = OrderStatus.CANCELLED;
        cancelledAt = at;
        updatedAt = at;
    }
    public long getPriceAmount() { return priceAmount; }

    public void pay(LocalDateTime at) {
        if (status != OrderStatus.AWAITING_PAYMENT) throw new BusinessException(HttpStatus.CONFLICT, "ORDER_STATE_CONFLICT", "결제 대기 주문만 결제할 수 있습니다.");
        if (at == null || at.getYear() < 1000 || at.getYear() > 9999 || at.isBefore(updatedAt)) throw new IllegalStateException("Invalid payment clock");
        var due = at.plusDays(durationDaysSnapshot);
        if (due.getYear() > 9999) throw new IllegalStateException("Payment due date outside database range");
        buyerConfirmedTermsAt = at;
        paidAt = at;
        dueAt = due;
        updatedAt = at;
        status = OrderStatus.IN_PROGRESS;
    }
    public Long getBuyerId() { return buyerId; }
    public int getDurationDaysSnapshot() { return durationDaysSnapshot; }

    public void requestRevision(LocalDateTime at) {
        if (status != OrderStatus.AWAITING_CONFIRMATION) {
            throw new BusinessException(HttpStatus.CONFLICT, "ORDER_STATE_CONFLICT", "납품 확인 대기 주문만 수정 요청할 수 있습니다.");
        }
        if (at == null || at.getYear() < 1000 || at.getYear() > 9999 || at.isBefore(updatedAt)) {
            throw new IllegalStateException("Invalid revision clock");
        }
        status = OrderStatus.REVISION_IN_PROGRESS;
        updatedAt = at.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    public void deliver(LocalDateTime at) {
        if (status != OrderStatus.IN_PROGRESS && status != OrderStatus.REVISION_IN_PROGRESS) {
            throw new BusinessException(HttpStatus.CONFLICT, "DELIVERY_SEQUENCE_CONFLICT", "현재 상태에서 제출할 수 없는 납품 회차입니다.");
        }
        if (at == null || at.getYear() < 1000 || at.getYear() > 9999 || at.isBefore(updatedAt)) {
            throw new IllegalStateException("Invalid delivery clock");
        }
        status = OrderStatus.AWAITING_CONFIRMATION;
        updatedAt = at.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
