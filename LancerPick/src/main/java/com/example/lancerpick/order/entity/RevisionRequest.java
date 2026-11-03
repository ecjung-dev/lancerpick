package com.example.lancerpick.order.entity;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.text.TextNormalizer;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.http.HttpStatus;

/** 주문당 최초 납품에 한 번만 요청하며, 최초 납기는 orders에 그대로 보존한다. */
@lombok.Getter
@Entity
@Table(name = "revision_requests")
public class RevisionRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;
    @Column(name = "delivery_id", nullable = false, updatable = false)
    private Long deliveryId;
    @Column(nullable = false, updatable = false, length = 1000)
    private String reason;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "requested_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime requestedAt;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "due_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime dueAt;

    protected RevisionRequest() {}

    public static RevisionRequest create(long orderId, long deliveryId, String reason, LocalDateTime at, int durationDays) {
        if (orderId <= 0 || deliveryId <= 0 || reason == null) throw invalidReason();
        String normalized = TextNormalizer.normalize(reason);
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 1000) throw invalidReason();
        if (at == null || at.getYear() < 1000 || at.getYear() > 9999 || durationDays < 1 || durationDays > 30) {
            throw new IllegalStateException("Invalid revision clock or stored duration");
        }
        LocalDateTime requestedAt = at.truncatedTo(ChronoUnit.MICROS);
        LocalDateTime dueAt = requestedAt.plusDays(durationDays);
        if (dueAt.getYear() > 9999) throw new IllegalStateException("Revision deadline exceeds MySQL range");
        RevisionRequest request = new RevisionRequest();
        request.orderId = orderId;
        request.deliveryId = deliveryId;
        request.reason = normalized;
        request.requestedAt = requestedAt;
        request.dueAt = dueAt;
        return request;
    }

    public String getReason() { return reason; }

    private static BusinessException invalidReason() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "수정 사유는 공백 정리 후 1~1000자여야 합니다.");
    }
}
