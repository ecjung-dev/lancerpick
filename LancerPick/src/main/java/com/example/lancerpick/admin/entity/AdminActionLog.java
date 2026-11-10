package com.example.lancerpick.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 요청 정보는 변경하지 않고 REQUESTED의 결과만 한 번 확정한다. Entity를 API 응답으로 쓰지 않는다. */
@Entity
@Table(name = "admin_action_logs")
@Getter
public class AdminActionLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "admin_id", nullable = false, updatable = false)
  private long adminId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 32, updatable = false)
  private AdminAction action;

  @Column(name = "target_type", nullable = false, length = 24, updatable = false)
  private String targetType;

  @Column(name = "target_id", nullable = false, updatable = false)
  private long targetId;

  @Column(name = "order_id", updatable = false)
  private Long orderId;

  @Column(nullable = false, length = 1000, updatable = false)
  private String reason;

  @Column(name = "request_key", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
  private String requestKey;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 16)
  private AdminActionStatus status;

  @Column(name = "result_code", length = 64)
  private String resultCode;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "requested_at", nullable = false, updatable = false)
  private LocalDateTime requestedAt;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "lease_until", nullable = false, updatable = false)
  private LocalDateTime leaseUntil;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  protected AdminActionLog() {}

  public static AdminActionLog request(
      long adminId,
      AdminAction action,
      long targetId,
      Long orderId,
      String reason,
      String requestKey,
      LocalDateTime at,
      LocalDateTime until) {
    var log = new AdminActionLog();
    log.adminId = adminId;
    log.action = action;
    log.targetType = action.targetType();
    log.targetId = targetId;
    log.orderId = orderId;
    log.reason = reason;
    log.requestKey = requestKey;
    log.status = AdminActionStatus.REQUESTED;
    log.requestedAt = at;
    log.leaseUntil = until;
    return log;
  }

  public boolean matches(AdminAction action, long targetId, String reason) {
    return this.action == action && this.targetId == targetId && this.reason.equals(reason);
  }

  public boolean complete(AdminActionStatus status, String code, LocalDateTime at) {
    if (status == null
        || status == AdminActionStatus.REQUESTED
        || code == null
        || at == null
        || at.isBefore(requestedAt)) {
      throw new IllegalArgumentException("Invalid audit outcome");
    }
    if (this.status != AdminActionStatus.REQUESTED) return false;
    this.status = status;
    this.resultCode = code;
    this.completedAt = at;
    return true;
  }
}
