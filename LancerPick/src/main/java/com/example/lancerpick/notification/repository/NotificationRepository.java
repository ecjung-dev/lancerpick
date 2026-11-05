package com.example.lancerpick.notification.repository;

import com.example.lancerpick.notification.entity.Notification;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  boolean existsByRecipientIdAndEventIdAndType(long recipientId, String eventId, String type);

  Optional<Notification> findByIdAndRecipientId(long id, long recipientId);

  long countByRecipientId(long recipientId);

  long countByRecipientIdAndReadAtIsNull(long recipientId);

  /** 아직 읽지 않은 본인 알림만 바꾼다. 동시 요청 중 먼저 커밋한 읽음 시각을 덮어쓰지 않는다. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update Notification n set n.readAt = :at where n.id = :id and n.recipientId = :recipientId"
          + " and n.readAt is null")
  int markReadIfUnread(
      @Param("id") long id, @Param("recipientId") long recipientId, @Param("at") LocalDateTime at);

  /** 생성 시각이 읽음 시각 이후인 알림은 CHECK 제약 때문에 건드리지 않는다. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update Notification n set n.readAt = :at where n.recipientId = :recipientId"
          + " and n.readAt is null and n.createdAt <= :at")
  int markAllRead(@Param("recipientId") long recipientId, @Param("at") LocalDateTime at);
}
