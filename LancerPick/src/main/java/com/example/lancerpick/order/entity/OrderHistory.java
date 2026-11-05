package com.example.lancerpick.order.entity;

import com.example.lancerpick.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.http.HttpStatus;

/** 발생한 행동과 당시 주문 상태를 기록하며 수정 메서드를 제공하지 않는다. */
@lombok.Getter
@Entity
@Table(name = "order_histories")
public class OrderHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Column(name = "actor_type", nullable = false, updatable = false, length = 10)
    private String actorType = "MEMBER";

    @Column(name = "source_event_key", updatable = false, length = 100)
    private String sourceEventKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", updatable = false, columnDefinition = "JSON")
    private String details;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "action", nullable = false, updatable = false, length = 40)
    private OrderHistoryAction action;

    // 최초 생성 이력에는 이전 상태가 없다.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_status", updatable = false, length = 32)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_status", nullable = false, updatable = false, length = 32)
    private OrderStatus toStatus;

    @Column(name = "reason", updatable = false, length = 1000)
    private String reason;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected OrderHistory() {
    }

    /** buyerId와 UTC 시각은 주문 생성 서비스가 제공한다. 객체 생성만으로 DB에 저장되지는 않는다. */
    public static OrderHistory created(Long orderId, Long buyerId, LocalDateTime createdAt) {
        if (orderId == null || orderId <= 0 || buyerId == null || buyerId <= 0) {
            throw invalidInput("주문 ID와 구매자 ID는 양수여야 합니다.");
        }
        if (createdAt == null || createdAt.getYear() < 1000 || createdAt.getYear() > 9999) {
            throw invalidInput("주문 이력 시각은 MySQL DATETIME 범위여야 합니다.");
        }

        OrderHistory history = new OrderHistory();
        history.orderId = orderId;
        history.actorId = buyerId;
        // 최초 이력의 상태 조합과 사유는 외부 입력으로 바꾸지 않는다.
        history.action = OrderHistoryAction.CREATED;
        history.fromStatus = null;
        history.toStatus = OrderStatus.REQUESTED;
        history.reason = null;
        history.createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
        return history;
    }

    private static BusinessException invalidInput(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    public static OrderHistory accepted(Long orderId, Long sellerId, LocalDateTime at) {
        return accepted(orderId, sellerId, at, OrderStatus.AWAITING_PAYMENT);
    }

    /** 계약 주문의 수락은 AWAITING_CONTRACT로 기록한다. */
    public static OrderHistory accepted(Long orderId, Long sellerId, LocalDateTime at, OrderStatus to) {
        if (to != OrderStatus.AWAITING_PAYMENT && to != OrderStatus.AWAITING_CONTRACT) throw invalidInput("수락 후 상태가 올바르지 않습니다.");
        return decision(orderId, sellerId, at, OrderHistoryAction.ACCEPTED, to, null);
    }

    /** 계약 초안의 새 버전이 만들어졌다. 주문 상태는 계약 대기로 유지된다. */
    public static OrderHistory contractCreated(Long orderId, Long sellerId, LocalDateTime at, int contractVersion) {
        if (contractVersion < 1) throw invalidInput("계약 버전이 올바르지 않습니다.");
        var history = decision(orderId, sellerId, at, OrderHistoryAction.CONTRACT_CREATED, OrderStatus.AWAITING_CONTRACT, null);
        history.fromStatus = OrderStatus.AWAITING_CONTRACT;
        history.details = "{\"contractVersion\": " + contractVersion + "}";
        return history;
    }

    /** 두 번째 서명으로 계약이 성립해 선금 대기로 넘어간다. 행위자는 마지막 서명자다. */
    public static OrderHistory contractSigned(Long orderId, Long signerId, LocalDateTime at, int contractVersion) {
        if (contractVersion < 1) throw invalidInput("계약 버전이 올바르지 않습니다.");
        var history = decision(orderId, signerId, at, OrderHistoryAction.CONTRACT_SIGNED, OrderStatus.AWAITING_PAYMENT, null);
        history.fromStatus = OrderStatus.AWAITING_CONTRACT;
        history.details = "{\"contractVersion\": " + contractVersion + "}";
        return history;
    }
    public static OrderHistory completed(Long orderId, Long buyerId, LocalDateTime at) {
        var history = decision(orderId, buyerId, at, OrderHistoryAction.COMPLETED, OrderStatus.COMPLETED, null);
        history.fromStatus = OrderStatus.AWAITING_CONFIRMATION;
        return history;
    }
    public static OrderHistory cancelled(Long orderId, Long actor, OrderStatus from, LocalDateTime at, String reason) {
        var history = decision(orderId, actor, at, OrderHistoryAction.CANCELLED, OrderStatus.CANCELLED, reason);
        history.fromStatus = from;
        return history;
    }

    public static OrderHistory paid(Long orderId, Long buyerId, LocalDateTime at) {
        var history = decision(orderId, buyerId, at, OrderHistoryAction.PAID, OrderStatus.IN_PROGRESS, null);
        history.fromStatus = OrderStatus.AWAITING_PAYMENT;
        return history;
    }

    public static OrderHistory delivered(Long orderId, Long sellerId, OrderStatus from, LocalDateTime at) {
        if (from != OrderStatus.IN_PROGRESS && from != OrderStatus.REVISION_IN_PROGRESS) throw invalidInput("납품 이전 상태가 올바르지 않습니다.");
        OrderHistory history = decision(orderId, sellerId, at, OrderHistoryAction.DELIVERED, OrderStatus.AWAITING_CONFIRMATION, null);
        history.fromStatus = from;
        return history;
    }

    public static OrderHistory revisionRequested(Long orderId, Long buyerId, LocalDateTime at, String reason) {
        if (reason == null) throw invalidInput("수정 사유가 필요합니다.");
        String normalized = com.example.lancerpick.common.text.TextNormalizer.normalize(reason);
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 1000) throw invalidInput("수정 사유는 1~1000자여야 합니다.");
        OrderHistory history = decision(orderId, buyerId, at, OrderHistoryAction.REVISION_REQUESTED, OrderStatus.REVISION_IN_PROGRESS, normalized);
        history.fromStatus = OrderStatus.AWAITING_CONFIRMATION;
        return history;
    }

    public static OrderHistory cancellationRequestEvent(Long orderId, Long actorId, OrderHistoryAction action,
            OrderStatus status, LocalDateTime at, String reason) {
        if (action != OrderHistoryAction.CANCELLATION_REQUESTED && action != OrderHistoryAction.CANCELLATION_REJECTED
                && action != OrderHistoryAction.CANCELLATION_WITHDRAWN
                && action != OrderHistoryAction.CANCELLATION_APPROVED) throw invalidInput("취소 요청 이력 행동이 올바르지 않습니다.");
        if (status != OrderStatus.IN_PROGRESS && status != OrderStatus.AWAITING_CONFIRMATION && status != OrderStatus.REVISION_IN_PROGRESS)
            throw invalidInput("유료 미완료 주문의 취소 요청만 기록합니다.");
        if (reason != null && (reason.isEmpty() || reason.codePointCount(0, reason.length()) > 1000)) throw invalidInput("취소 요청 사유가 올바르지 않습니다.");
        if (action == OrderHistoryAction.CANCELLATION_REQUESTED && reason == null) throw invalidInput("취소 요청 사유가 필요합니다.");
        OrderHistory history = decision(orderId, actorId, at, action, status, reason);
        history.fromStatus = status;
        return history;
    }

    public static OrderHistory rejected(Long orderId, Long sellerId, LocalDateTime at, String reason) {
        if (reason == null) throw invalidInput("거절 사유가 필요합니다.");
        String normalized = com.example.lancerpick.common.text.TextNormalizer.normalize(reason);
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 1000) {
            throw invalidInput("거절 사유는 공백 정리 후 1~1000자여야 합니다.");
        }
        return decision(orderId, sellerId, at, OrderHistoryAction.REJECTED, OrderStatus.REJECTED, normalized);
    }

    private static OrderHistory decision(Long orderId, Long sellerId, LocalDateTime at,
            OrderHistoryAction action, OrderStatus toStatus, String reason) {
        // 기존 생성 팩터리의 ID·시각 검사를 공통으로 사용한다.
        OrderHistory history = created(orderId, sellerId, at);
        history.action = action;
        history.fromStatus = OrderStatus.REQUESTED;
        history.toStatus = toStatus;
        history.reason = reason;
        return history;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getActorId() { return actorId; }
    public OrderHistoryAction getAction() { return action; }
    public OrderStatus getFromStatus() { return fromStatus; }
    public OrderStatus getToStatus() { return toStatus; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
