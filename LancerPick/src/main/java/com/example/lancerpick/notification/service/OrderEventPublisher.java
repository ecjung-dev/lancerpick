package com.example.lancerpick.notification.service;

import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.notification.entity.OutboxEvent;
import com.example.lancerpick.notification.repository.OutboxEventRepository;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderHistory;
import com.example.lancerpick.order.entity.OrderHistoryAction;
import com.example.lancerpick.order.entity.OrderResponseTimer;
import com.example.lancerpick.order.entity.OrderStatus;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문 이력과 같은 트랜잭션에서 아웃박스 이벤트를 저장한다. 저장 실패는 주문 변경도 롤백한다.
 * 알림 전달은 처리기가 별도 트랜잭션에서 수행하므로 전달 실패가 완료된 거래를 되돌리지 않는다.
 */
@Service
@Profile("!demo")
public class OrderEventPublisher {
  static final String AGGREGATE_ORDER = "ORDER";
  /**
   * ORDER 집계 페이로드 버전. 필드를 추가만 하면 그대로 두고, 필드 의미를 바꾸거나 지우면 올린다. 올릴 때는 처리기가 이전
   * 버전도 계속 읽도록 {@link OutboxDispatcher}의 지원 목록에 함께 추가한다(이미 저장된 PENDING 이벤트가 있다).
   */
  static final int ORDER_SCHEMA_VERSION = 1;
  public static final String AUTO_CONFIRM_SOON = "ORDER_AUTO_CONFIRM_SOON";
  private static final String AUTO_CONFIRM_NOTICE_KIND = "AUTO_CONFIRM_NOTICE";
  private static final Set<OrderStatus> PAID_WORK =
      EnumSet.of(
          OrderStatus.IN_PROGRESS,
          OrderStatus.AWAITING_CONFIRMATION,
          OrderStatus.REVISION_IN_PROGRESS);

  // 승인은 같은 트랜잭션의 CANCELLED 이력과 수신자가 같아 중복 알림이 되므로 제외한다.
  // 타이머 종료(구매자 응답)는 함께 기록되는 수정 요청·취소 요청·구매 확정 알림이 대신한다.
  private static final Set<OrderHistoryAction> SILENT =
      EnumSet.of(OrderHistoryAction.CANCELLATION_APPROVED, OrderHistoryAction.TIMER_CANCELLED);

  private final OutboxEventRepository events;
  private final JsonMapper mapper;

  public OrderEventPublisher(OutboxEventRepository events, JsonMapper mapper) {
    this.events = events;
    this.mapper = mapper;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void publish(Order order, OrderHistory history) {
    Objects.requireNonNull(order, "order");
    Objects.requireNonNull(history, "history");
    if (history.getId() == null || !history.getOrderId().equals(order.getId()))
      throw new IllegalStateException("History must be saved for the same order");
    if (SILENT.contains(history.getAction())) return;
    List<Long> recipients = recipients(order, history.getActorId());
    if (recipients.isEmpty()) return;
    var payload = new TreeMap<String, Object>();
    payload.put("orderId", order.getId().toString());
    payload.put("historyId", history.getId().toString());
    payload.put("action", history.getAction().name());
    payload.put("recipientIds", recipients.stream().map(String::valueOf).toList());
    events.saveAndFlush(
        OutboxEvent.pending(
            notificationType(order, history),
            ORDER_SCHEMA_VERSION,
            AGGREGATE_ORDER,
            order.getId(),
            mapper.writeValueAsString(payload),
            history.getCreatedAt()));
  }

  /**
   * 자동 구매 확정 사전 안내(E9)를 구매자에게 보낸다. 상태 변화가 아니므로 이력 없이 이벤트만 저장한다. 이벤트 ID를 타이머 ID로
   * 정해 두어 event_id UNIQUE가 같은 타이머의 중복 안내를 막는다. 호출자가 주문 잠금 아래 타이머가 활성인지 확인한다.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean publishAutoConfirmNotice(Order order, OrderResponseTimer timer, LocalDateTime at) {
    Objects.requireNonNull(order, "order");
    Objects.requireNonNull(timer, "timer");
    if (timer.getOrderId() != order.getId()) throw new IllegalStateException("Timer must belong to the order");
    String eventId = autoConfirmNoticeEventId(timer.getId());
    if (events.existsByEventId(eventId)) return false;
    var payload = new TreeMap<String, Object>();
    payload.put("orderId", order.getId().toString());
    payload.put("timerId", timer.getId().toString());
    payload.put("action", "AUTO_CONFIRM_NOTICE");
    payload.put("expiresAt", timer.getExpiresAt().atOffset(ZoneOffset.UTC).toString());
    payload.put("recipientIds", List.of(String.valueOf(order.getBuyerId())));
    events.saveAndFlush(
        OutboxEvent.pending(
            eventId,
            AUTO_CONFIRM_SOON,
            ORDER_SCHEMA_VERSION,
            AGGREGATE_ORDER,
            order.getId(),
            mapper.writeValueAsString(payload),
            at));
    return true;
  }

  /** 같은 타이머에는 항상 같은 이벤트 ID가 나온다. kind를 바꾸면 이미 저장된 안내와 중복되므로 바꾸지 않는다. */
  public static String autoConfirmNoticeEventId(long timerId) {
    return OutboxEvent.nameBasedId(AUTO_CONFIRM_NOTICE_KIND, timerId);
  }

  static String notificationType(OrderHistoryAction action) {
    return "ORDER_" + action.name();
  }

  /**
   * 시스템 구매 확정은 구매자 확정과 문구가 달라 별도 유형으로 보낸다. V3 주문의 결제·구매 확정·결제 후 취소는 보관·지급·반환을
   * 안내하는 유형으로, 기한 통지는 납기·재납품 기한 유형으로 보낸다. 유형은 저장 시점에 고정되어 이후 정책 변경에 바뀌지 않는다.
   */
  static String notificationType(Order order, OrderHistory history) {
    var action = history.getAction();
    if (action == OrderHistoryAction.COMPLETED && "SYSTEM".equals(history.getActorType()))
      return "ORDER_AUTO_CONFIRMED";
    if (action == OrderHistoryAction.DEADLINE_NOTIFIED)
      return history.getToStatus() == OrderStatus.REVISION_IN_PROGRESS
          ? "ORDER_REDELIVERY_DUE_NOTIFIED"
          : "ORDER_DELIVERY_DUE_NOTIFIED";
    if (TradePolicyVersion.CONTRACT_V3.name().equals(order.getPolicyVersion())) {
      if (action == OrderHistoryAction.PAID) return "ORDER_FUNDS_HELD";
      if (action == OrderHistoryAction.COMPLETED) return "ORDER_FUNDS_RELEASED";
      if (action == OrderHistoryAction.CANCELLED && PAID_WORK.contains(history.getFromStatus()))
        return "ORDER_FUNDS_REFUNDED";
    }
    return notificationType(action);
  }

  /** 행동한 당사자에게는 알리지 않는다. 시스템 처리(actor 없음)는 양측 모두에게 알린다. */
  private static List<Long> recipients(Order order, Long actorId) {
    long buyer = order.getBuyerId(), seller = order.getSellerId();
    if (actorId == null) return List.of(buyer, seller);
    if (actorId == buyer) return List.of(seller);
    if (actorId == seller) return List.of(buyer);
    return List.of(buyer, seller);
  }
}
