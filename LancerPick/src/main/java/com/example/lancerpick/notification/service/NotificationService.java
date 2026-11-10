package com.example.lancerpick.notification.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.notification.dto.NotificationResponse;
import com.example.lancerpick.notification.dto.UnreadCountResponse;
import com.example.lancerpick.notification.repository.NotificationQueries;
import com.example.lancerpick.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")
public class NotificationService {
  private final NotificationRepository notifications;
  private final NotificationQueries queries;
  private final Clock clock;

  public NotificationService(NotificationRepository notifications, NotificationQueries queries, Clock clock) {
    this.notifications = notifications;
    this.queries = queries;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  /** unreadOnly면 읽지 않은 알림만 같은 순서로 보여 주고, 건수도 같은 조건으로 센다. */
  public PageResponse<NotificationResponse> list(long memberId, int page, int size, boolean unreadOnly) {
    if (!PageResponse.isValidRequest(page, size))
      throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "페이지 조건이 올바르지 않습니다.");
    long total =
        unreadOnly
            ? notifications.countByRecipientIdAndReadAtIsNull(memberId)
            : notifications.countByRecipientId(memberId);
    long offset = (long) page * size;
    var items =
        offset >= total ? List.<NotificationResponse>of() : queries.find(memberId, unreadOnly, size, offset);
    return new PageResponse<>(
        items, page, size, total, PageResponse.totalPages(total, size));
  }

  @Transactional(readOnly = true)
  public UnreadCountResponse unreadCount(long memberId) {
    return new UnreadCountResponse(notifications.countByRecipientIdAndReadAtIsNull(memberId));
  }

  /** 본인 알림이 아니면 존재 여부를 드러내지 않고 404로 응답한다. 이미 읽은 알림은 그대로 성공한다. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void markRead(long memberId, String id) {
    long notificationId;
    try {
      if (id == null || !id.matches("[0-9]+")) throw invalidId();
      notificationId = Long.parseLong(id);
      if (notificationId <= 0) throw invalidId();
    } catch (NumberFormatException e) {
      throw invalidId();
    }
    var notification =
        notifications
            .findByIdAndRecipientId(notificationId, memberId)
            .orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "알림을 찾을 수 없습니다."));
    // DB CHECK(read_at >= created_at)에 맞춰 생성 시각보다 이르게 기록하지 않는다.
    var at = now();
    if (at.isBefore(notification.getCreatedAt())) at = notification.getCreatedAt();
    notifications.markReadIfUnread(notificationId, memberId, at);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void markAllRead(long memberId) {
    notifications.markAllRead(memberId, now());
  }

  private LocalDateTime now() {
    return UtcTimes.nowMicros(clock);
  }

  private static BusinessException invalidId() {
    return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "알림 ID는 양의 정수 범위여야 합니다.");
  }
}
