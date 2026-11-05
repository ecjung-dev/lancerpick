package com.example.lancerpick.notification.entity;

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

/** 수신자별 알림. 같은 이벤트·유형은 수신자당 한 건만 존재한다(DB UNIQUE). */
@Getter
@Entity
@Table(name = "notifications")
public class Notification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "recipient_id", nullable = false, updatable = false)
  private long recipientId;

  @Column(name = "event_id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
  private String eventId;

  @Column(nullable = false, updatable = false, length = 60)
  private String type;

  @Column(name = "order_id", updatable = false)
  private Long orderId;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "read_at")
  private LocalDateTime readAt;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected Notification() {}

  public static Notification of(
      long recipientId, String eventId, String type, Long orderId, LocalDateTime at) {
    if (recipientId <= 0 || eventId == null || type == null || type.isBlank() || type.length() > 60
        || (orderId != null && orderId <= 0) || at == null)
      throw new IllegalArgumentException("Invalid notification");
    var notification = new Notification();
    notification.recipientId = recipientId;
    notification.eventId = eventId;
    notification.type = type;
    notification.orderId = orderId;
    notification.createdAt = at.truncatedTo(ChronoUnit.MICROS);
    return notification;
  }
}
