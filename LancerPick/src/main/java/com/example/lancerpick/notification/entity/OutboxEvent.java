package com.example.lancerpick.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 업무 변경과 같은 트랜잭션에 저장되는 처리 대기 이벤트. 처리기는 행 잠금 아래에서 한 번만 완료한다.
 * lease 컬럼은 트랜잭션 밖 장시간 처리용으로 예약되어 있으며 현재 DB 내부 처리에서는 사용하지 않는다.
 */
@Getter
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
  public static final String PENDING = "PENDING";
  public static final String COMPLETED = "COMPLETED";
  public static final String FAILED = "FAILED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
  private String eventId;

  @Column(nullable = false, updatable = false, length = 60)
  private String type;

  @Column(name = "schema_version", nullable = false, updatable = false)
  private int schemaVersion;

  @Column(name = "aggregate_type", nullable = false, updatable = false, length = 30)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false, updatable = false)
  private long aggregateId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, updatable = false, columnDefinition = "JSON")
  private String payload;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "next_attempt_at", nullable = false)
  private LocalDateTime nextAttemptAt;

  @Column(name = "lease_owner", columnDefinition = "CHAR(36)")
  private String leaseOwner;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "lease_until")
  private LocalDateTime leaseUntil;

  @Column(name = "last_error_code", length = 100)
  private String lastErrorCode;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "processed_at")
  private LocalDateTime processedAt;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected OutboxEvent() {}

  public static OutboxEvent pending(
      String type, String aggregateType, long aggregateId, String payload, LocalDateTime at) {
    if (type == null || type.isBlank() || type.length() > 60)
      throw new IllegalArgumentException("Invalid event type");
    if (aggregateType == null || aggregateType.isBlank() || aggregateType.length() > 30)
      throw new IllegalArgumentException("Invalid aggregate type");
    if (aggregateId <= 0 || payload == null || at == null)
      throw new IllegalArgumentException("Invalid outbox event");
    var event = new OutboxEvent();
    event.eventId = UUID.randomUUID().toString();
    event.type = type;
    event.schemaVersion = 1;
    event.aggregateType = aggregateType;
    event.aggregateId = aggregateId;
    event.payload = payload;
    event.status = PENDING;
    event.attemptCount = 0;
    event.createdAt = at.truncatedTo(ChronoUnit.MICROS);
    event.nextAttemptAt = event.createdAt;
    return event;
  }

  public boolean isDue(LocalDateTime now) {
    return PENDING.equals(status) && !nextAttemptAt.isAfter(now);
  }

  public void complete(LocalDateTime at) {
    if (!PENDING.equals(status) || at == null) throw new IllegalStateException("Event is not pending");
    status = COMPLETED;
    processedAt = at.truncatedTo(ChronoUnit.MICROS);
    lastErrorCode = null;
  }

  /** 실패 횟수를 늘리고 다음 시도 시각을 미룬다. 최대 횟수에 도달하면 FAILED로 멈춘다. */
  public void fail(String errorCode, LocalDateTime at, int maxAttempts, long backoffSeconds) {
    if (!PENDING.equals(status) || at == null || maxAttempts < 1 || backoffSeconds < 0)
      throw new IllegalStateException("Event is not pending");
    attemptCount++;
    lastErrorCode = errorCode == null ? null : errorCode.substring(0, Math.min(100, errorCode.length()));
    nextAttemptAt = at.plusSeconds(backoffSeconds).truncatedTo(ChronoUnit.MICROS);
    if (attemptCount >= maxAttempts) status = FAILED;
  }
}
