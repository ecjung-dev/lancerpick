package com.example.lancerpick.notification.service;

import com.example.lancerpick.notification.entity.OutboxEvent;
import com.example.lancerpick.notification.repository.OutboxEventRepository;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderHistory;
import com.example.lancerpick.order.entity.OrderHistoryAction;
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
            notificationType(history),
            AGGREGATE_ORDER,
            order.getId(),
            mapper.writeValueAsString(payload),
            history.getCreatedAt()));
  }

  static String notificationType(OrderHistoryAction action) {
    return "ORDER_" + action.name();
  }

  /** 시스템 구매 확정은 구매자 확정과 문구가 달라 별도 유형으로 보낸다. */
  static String notificationType(OrderHistory history) {
    if (history.getAction() == OrderHistoryAction.COMPLETED && "SYSTEM".equals(history.getActorType()))
      return "ORDER_AUTO_CONFIRMED";
    return notificationType(history.getAction());
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
