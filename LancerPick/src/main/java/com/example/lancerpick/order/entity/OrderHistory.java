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
@Entity
@Table(name = "order_histories")
public class OrderHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private Long actorId;

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

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getActorId() { return actorId; }
    public OrderHistoryAction getAction() { return action; }
    public OrderStatus getFromStatus() { return fromStatus; }
    public OrderStatus getToStatus() { return toStatus; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
