package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.order.dto.OrderDetailResponse;
import com.example.lancerpick.order.entity.*;
import com.example.lancerpick.notification.service.OrderEventPublisher;
import com.example.lancerpick.order.repository.*;
import com.example.lancerpick.payment.repository.PaymentRepository;
import com.example.lancerpick.settlement.entity.Settlement;
import com.example.lancerpick.settlement.repository.SettlementRepository;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Profile("!demo")
public class OrderConfirmationService {
  private final OrderRepository orders;
  private final OrderHistoryRepository histories;
  private final OrderEventPublisher events;
  private final OrderDetailQueries details;
  private final PaymentRepository payments;
  private final SettlementRepository settlements;
  private final CancellationRequestRepository requests;
  private final Clock clock;

  public OrderConfirmationService(
      OrderRepository orders,
      OrderHistoryRepository histories,
      OrderEventPublisher events,
      OrderDetailQueries details,
      PaymentRepository payments,
      SettlementRepository settlements,
      CancellationRequestRepository requests,
      Clock clock) {
    this.orders = orders;
    this.histories = histories;
    this.events = events;
    this.details = details;
    this.payments = payments;
    this.settlements = settlements;
    this.requests = requests;
    this.clock = clock;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public OrderDetailResponse confirm(long memberId, String id) {
    long orderId;
    try {
      if (id == null || !id.matches("[0-9]+") || (orderId = Long.parseLong(id)) <= 0)
        throw new NumberFormatException();
    } catch (NumberFormatException e) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "주문 ID를 확인해 주세요.");
    }
    if (orders.lockDecisionActor(memberId).isEmpty())
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    var order =
        orders
            .findForLegacyDecision(orderId, memberId)
            .orElseThrow(() ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "주문을 찾을 수 없습니다."));
    if (!order.getBuyerId().equals(memberId))
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "구매자만 구매 확정할 수 있습니다.");
    boolean completed = order.getStatus() == OrderStatus.COMPLETED;
    if (!completed && order.getStatus() != OrderStatus.AWAITING_CONFIRMATION)
      throw new BusinessException(
          HttpStatus.CONFLICT, "ORDER_STATE_CONFLICT", "검수 대기 주문만 구매 확정할 수 있습니다.");
    if (!requests.findByOrderIdAndStatus(orderId, "PENDING").isEmpty())
      throw new BusinessException(
          HttpStatus.CONFLICT, "CANCELLATION_PENDING", "대기 중인 취소 요청을 먼저 처리해 주세요.");
    var payment = payments.findByOrderId(orderId).orElseThrow(OrderConfirmationService::integrity);
    if (!"PAID".equals(payment.status())
        || payment.amount() != order.getPriceAmount()
        || order.getPaidAt() == null) throw integrity();
    long fee = Math.multiplyExact(payment.amount(), payment.rate()) / 10000;
    long seller = payment.amount() - fee;
    var existing = settlements.findByOrderId(orderId);
    if (completed) {
      if (existing.isEmpty()
          || !java.util.Arrays.equals(
              existing.get().amounts(), new long[] {payment.amount(), payment.rate(), fee, seller}))
        throw integrity();
    } else {
      if (existing.isPresent()) throw integrity();
      var at =
          LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
      order.confirm(at);
      settlements.saveAndFlush(Settlement.create(orderId, payment.amount(), payment.rate(), at));
      events.publish(order, histories.saveAndFlush(OrderHistory.completed(orderId, memberId, at)));
    }
    var detail = details.forParticipant(orderId, memberId);
    return detail.withAllowedActions(
        OrderAllowedActions.actions(
            detail.policyVersion(), detail.status(),
            true,
            detail.revisionRequest(),
            detail.cancellationRequests(),
            detail.review()));
  }

  private static BusinessException integrity() {
    return new BusinessException(
        HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY_ERROR", "정산 기록을 확인해야 합니다.");
  }
}
