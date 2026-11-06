package com.example.lancerpick.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * V3 검수 무응답 자동 구매 확정 타이머. 기산점+48시간 이후 판매자가 시작하고 시작+168시간에 만료된다. 상태 조합은 DB CHECK와
 * 같게 엔티티에서 먼저 지킨다. 시각은 DATETIME(6)과 정확히 비교되도록 마이크로초로 자른다.
 */
@Getter
@Entity
@Table(name = "order_response_timers")
public class OrderResponseTimer {
  public static final String AUTO_CONFIRM = "AUTO_CONFIRM";
  public static final String ACTIVE = "ACTIVE";
  public static final String EXPIRED = "EXPIRED";
  public static final String CANCELLED = "CANCELLED";
  public static final long ELIGIBLE_AFTER_HOURS = 48;
  public static final long EXPIRES_AFTER_HOURS = 168;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false, updatable = false)
  private long orderId;

  @Column(name = "delivery_id", nullable = false, updatable = false)
  private long deliveryId;

  @Column(name = "timer_type", nullable = false, updatable = false, length = 24)
  private String timerType;

  @Column(name = "cycle_no", nullable = false, updatable = false)
  private int cycleNo;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "basis_at", nullable = false, updatable = false)
  private LocalDateTime basisAt;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "eligible_at", nullable = false, updatable = false)
  private LocalDateTime eligibleAt;

  @Column(name = "started_by", nullable = false, updatable = false)
  private long startedBy;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "started_at", nullable = false, updatable = false)
  private LocalDateTime startedAt;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "expires_at", nullable = false, updatable = false)
  private LocalDateTime expiresAt;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(name = "active_slot")
  private Integer activeSlot;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "ended_at")
  private LocalDateTime endedAt;

  @Column(name = "ended_by")
  private Long endedBy;

  @Column(name = "end_reason", length = 24)
  private String endReason;

  @Column(name = "admin_stop_reason", length = 1000)
  private String adminStopReason;

  protected OrderResponseTimer() {}

  public static LocalDateTime eligibleAt(LocalDateTime basisAt) {
    return basisAt.truncatedTo(ChronoUnit.MICROS).plusHours(ELIGIBLE_AFTER_HOURS);
  }

  public static OrderResponseTimer startAutoConfirm(
      long orderId,
      long deliveryId,
      int cycleNo,
      LocalDateTime basisAt,
      long sellerId,
      LocalDateTime startedAt) {
    if (orderId < 1 || deliveryId < 1 || cycleNo < 1 || sellerId < 1)
      throw new IllegalArgumentException("Invalid review timer");
    var basis = basisAt.truncatedTo(ChronoUnit.MICROS);
    var started = startedAt.truncatedTo(ChronoUnit.MICROS);
    var eligible = eligibleAt(basis);
    if (started.isBefore(eligible)) throw new IllegalArgumentException("Timer is not eligible yet");
    var timer = new OrderResponseTimer();
    timer.orderId = orderId;
    timer.deliveryId = deliveryId;
    timer.timerType = AUTO_CONFIRM;
    timer.cycleNo = cycleNo;
    timer.basisAt = basis;
    timer.eligibleAt = eligible;
    timer.startedBy = sellerId;
    timer.startedAt = started;
    timer.expiresAt = started.plusHours(EXPIRES_AFTER_HOURS);
    timer.status = ACTIVE;
    timer.activeSlot = 1;
    return timer;
  }

  public boolean isActive() {
    return ACTIVE.equals(status);
  }

  public boolean isDue(LocalDateTime now) {
    return isActive() && !now.isBefore(expiresAt);
  }

  /** 구매자 응답(BUYER_RESPONSE)이나 구매자 확정(ORDER_CLOSED)으로 만료 전에 끝낸다. */
  public void cancel(long memberId, String reason, LocalDateTime at) {
    if (!"BUYER_RESPONSE".equals(reason) && !"ORDER_CLOSED".equals(reason))
      throw new IllegalArgumentException("Unsupported timer end reason");
    end(CANCELLED, memberId, reason, at);
  }

  public void adminStop(long adminId, String reason, LocalDateTime at) {
    if (reason == null || reason.isBlank() || reason.codePointCount(0, reason.length()) > 1000)
      throw new IllegalArgumentException("Invalid admin stop reason");
    end(CANCELLED, adminId, "ADMIN_STOP", at);
    adminStopReason = reason;
  }

  public void expire(LocalDateTime at) {
    if (at.isBefore(expiresAt)) throw new IllegalStateException("Timer has not expired");
    end(EXPIRED, null, "TIMEOUT", at);
  }

  private void end(String nextStatus, Long memberId, String reason, LocalDateTime at) {
    if (!isActive()) throw new IllegalStateException("Timer is not active");
    var endedAtValue = at.truncatedTo(ChronoUnit.MICROS);
    if (endedAtValue.isBefore(startedAt)) throw new IllegalStateException("Timer clock moved backwards");
    status = nextStatus;
    activeSlot = null;
    endedAt = endedAtValue;
    endedBy = memberId;
    endReason = reason;
  }
}
