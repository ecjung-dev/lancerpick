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

import java.time.LocalDateTime;
import java.util.UUID;

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
    @Column(name = "status", nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "price_amount", nullable = false, updatable = false)
    private Long priceAmount;

    @Column(name = "duration_days_snapshot", nullable = false, updatable = false)
    private Integer durationDaysSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "buyer_confirmed_terms_at")
    private LocalDateTime buyerConfirmedTermsAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

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
        order.createdAt = now;
        order.updatedAt = now;

        return order;
    }

    public OrderStatus getStatus() {
        return status;
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