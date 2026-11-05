package com.example.lancerpick.notification.service;

import com.example.lancerpick.notification.entity.Notification;
import com.example.lancerpick.notification.entity.OutboxEvent;
import com.example.lancerpick.notification.repository.NotificationRepository;
import com.example.lancerpick.notification.repository.OutboxEventRepository;
import com.example.lancerpick.order.repository.OrderRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 아웃박스 이벤트를 수신자별 알림으로 바꾼다. 이벤트마다 별도 트랜잭션을 사용해 한 건의 실패가 다른 이벤트를 막지 않는다.
 * 같은 이벤트를 여러 처리기가 동시에 고르더라도 행 잠금 후 상태를 다시 확인하므로 한 번만 완료된다.
 */
@Service
@Profile("!demo")
public class OutboxDispatcher {
  static final int MAX_ATTEMPTS = 5;
  private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

  private final OutboxEventRepository events;
  private final NotificationRepository notifications;
  private final OrderRepository orders;
  private final JsonMapper mapper;
  private final Clock clock;
  private final TransactionTemplate tx;

  public OutboxDispatcher(
      OutboxEventRepository events,
      NotificationRepository notifications,
      OrderRepository orders,
      JsonMapper mapper,
      Clock clock,
      PlatformTransactionManager transactions) {
    this.events = events;
    this.notifications = notifications;
    this.orders = orders;
    this.mapper = mapper;
    this.clock = clock;
    this.tx = new TransactionTemplate(transactions);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  /** 처리 시점에 기한이 된 이벤트를 최대 limit건 처리하고 완료한 건수를 반환한다. */
  public int dispatchDue(int limit) {
    if (limit < 1 || limit > 500) throw new IllegalArgumentException("Invalid dispatch limit");
    List<Long> ids = tx.execute(status -> events.findDueIds(now(), PageRequest.of(0, limit)));
    int completed = 0;
    for (long id : ids == null ? List.<Long>of() : ids) {
      if (dispatch(id)) completed++;
    }
    return completed;
  }

  /** 이벤트 한 건을 처리한다. 이미 완료됐거나 기한 전이면 false, 실패하면 재시도 정보를 남기고 false를 반환한다. */
  public boolean dispatch(long id) {
    try {
      return Boolean.TRUE.equals(tx.execute(status -> process(id)));
    } catch (RuntimeException exception) {
      // 페이로드·개인정보를 남기지 않고 이벤트 ID와 예외 유형만 기록한다.
      log.warn("Outbox event {} failed: {}", id, exception.getClass().getSimpleName());
      try {
        tx.executeWithoutResult(status -> recordFailure(id, exception));
      } catch (RuntimeException recordException) {
        // 실패 기록마저 실패해도(잠금 대기 초과 등) 같은 배치의 다음 이벤트 처리를 막지 않는다.
        // 이벤트는 PENDING으로 남아 다음 배치에서 다시 선택된다.
        log.warn(
            "Outbox event {} failure was not recorded: {}",
            id,
            recordException.getClass().getSimpleName());
      }
      return false;
    }
  }

  private boolean process(long id) {
    var event = events.lockById(id).orElse(null);
    var at = now();
    if (event == null || !event.isDue(at)) return false;
    for (Notification notification : toNotifications(event)) {
      if (!notifications.existsByRecipientIdAndEventIdAndType(
          notification.getRecipientId(), notification.getEventId(), notification.getType()))
        notifications.save(notification);
    }
    event.complete(at);
    events.flush();
    return true;
  }

  private List<Notification> toNotifications(OutboxEvent event) {
    if (!OrderEventPublisher.AGGREGATE_ORDER.equals(event.getAggregateType()) || event.getSchemaVersion() != 1)
      throw new UnsupportedEventException();
    JsonNode payload;
    try {
      payload = mapper.readTree(event.getPayload());
    } catch (JacksonException e) {
      throw new UnsupportedEventException();
    }
    JsonNode recipients = payload.get("recipientIds");
    if (recipients == null || !recipients.isArray() || recipients.isEmpty()) throw new UnsupportedEventException();
    // 페이로드를 신뢰하지 않는다. 현재 주문의 구매자·판매자가 아닌 수신자가 있으면 이벤트 전체를 실패 처리한다.
    var order = orders.findById(event.getAggregateId()).orElseThrow(UnsupportedEventException::new);
    var recipientIds = new LinkedHashSet<Long>();
    for (JsonNode recipient : recipients) {
      long recipientId;
      try {
        recipientId = Long.parseLong(recipient.asString());
      } catch (RuntimeException e) {
        throw new UnsupportedEventException();
      }
      if (recipientId != order.getBuyerId() && recipientId != order.getSellerId())
        throw new UnsupportedEventException();
      recipientIds.add(recipientId);
    }
    var result = new ArrayList<Notification>();
    for (long recipientId : recipientIds) {
      result.add(
          Notification.of(
              recipientId,
              event.getEventId(),
              event.getType(),
              event.getAggregateId(),
              event.getCreatedAt()));
    }
    return result;
  }

  private void recordFailure(long id, RuntimeException exception) {
    var event = events.lockById(id).orElse(null);
    var at = now();
    // 기한이 지난 PENDING만 기록한다. 같은 이벤트를 고른 다른 처리기가 이미 실패를 기록해 다음 시도를 미뤘다면
    // 이번 실패는 같은 시도로 보고 횟수를 다시 올리지 않는다(동시 경합만으로 FAILED가 되는 것을 막는다).
    if (event == null || !event.isDue(at)) return;
    // 형식·수신자 오류는 다시 시도해도 성공할 수 없으므로 바로 FAILED로 멈춘다.
    int maxAttempts =
        exception instanceof UnsupportedEventException ? event.getAttemptCount() + 1 : MAX_ATTEMPTS;
    long backoff = Math.min(3600, 30L << Math.min(event.getAttemptCount(), 7));
    event.fail(exception.getClass().getSimpleName(), at, maxAttempts, backoff);
    events.flush();
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }

  static final class UnsupportedEventException extends RuntimeException {
    UnsupportedEventException() {
      super("Unsupported outbox event", null, false, false);
    }
  }
}
