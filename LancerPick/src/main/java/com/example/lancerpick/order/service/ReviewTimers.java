package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.notification.service.OrderEventPublisher;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderHistory;
import com.example.lancerpick.order.entity.OrderResponseTimer;
import com.example.lancerpick.order.repository.OrderHistoryRepository;
import com.example.lancerpick.order.repository.OrderResponseTimerRepository;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매자 응답(수정 요청·취소 요청·구매 확정) 시 활성 자동 구매 확정 타이머를 끝낸다. 주문 배타 잠금을 가진 호출자의 트랜잭션에
 * 참여한다. 만료 시각이 지난 활성 타이머는 만료 처리가 우선이므로 취소하지 않고 충돌로 응답한다.
 */
@Component
@Profile("!demo")
public class ReviewTimers {
  static final String BUYER_RESPONSE = "BUYER_RESPONSE";
  static final String ORDER_CLOSED = "ORDER_CLOSED";

  private final OrderResponseTimerRepository timers;
  private final OrderHistoryRepository histories;
  private final OrderEventPublisher events;

  ReviewTimers(
      OrderResponseTimerRepository timers,
      OrderHistoryRepository histories,
      OrderEventPublisher events) {
    this.timers = timers;
    this.histories = histories;
    this.events = events;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void closeForBuyerResponse(Order order, long buyerId, LocalDateTime at, String endReason) {
    var active = timers.findByOrderIdAndStatus(order.getId(), OrderResponseTimer.ACTIVE);
    if (active.isEmpty()) return;
    var timer = active.get();
    if (order.getBuyerId() != buyerId) throw new IllegalArgumentException("Buyer response required");
    if (timer.isDue(at)) throw autoConfirmDue();
    timer.cancel(buyerId, endReason, at);
    timers.flush();
    events.publish(
        order,
        histories.saveAndFlush(
            OrderHistory.timerCancelled(
                order.getId(), buyerId, order.getStatus(), at, timer.getId(), endReason)));
  }

  /** 활성 타이머가 남아 있으면 안 되는 금액 명령(결제·승인 환불)의 무결성 확인. */
  public void requireNoActive(Order order) {
    if (timers.existsByOrderIdAndStatus(order.getId(), OrderResponseTimer.ACTIVE))
      throw new BusinessException(
          HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY_ERROR", "검수 타이머 기록을 확인해야 합니다.");
  }

  static BusinessException autoConfirmDue() {
    return new BusinessException(
        HttpStatus.CONFLICT,
        "AUTO_CONFIRM_DUE",
        "자동 구매 확정 시각이 지나 처리 중입니다. 잠시 후 주문 상태를 다시 확인해 주세요.");
  }
}
