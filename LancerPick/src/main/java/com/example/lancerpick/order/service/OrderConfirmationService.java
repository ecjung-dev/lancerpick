package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.notification.service.OrderEventPublisher;
import com.example.lancerpick.order.dto.OrderDetailResponse;
import com.example.lancerpick.order.entity.CancellationRequestStatus;
import com.example.lancerpick.order.entity.OrderHistory;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.repository.CancellationRequestRepository;
import com.example.lancerpick.order.repository.OrderDetailQueries;
import com.example.lancerpick.order.repository.OrderHistoryRepository;
import com.example.lancerpick.payment.entity.PaymentStatus;
import com.example.lancerpick.payment.repository.PaymentRepository;
import com.example.lancerpick.settlement.entity.Settlement;
import com.example.lancerpick.settlement.repository.SettlementRepository;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")
public class OrderConfirmationService {
  private final OrderCommandLocks locks;
  private final OrderHistoryRepository histories;
  private final OrderEventPublisher events;
  private final OrderDetailQueries details;
  private final PaymentRepository payments;
  private final SettlementRepository settlements;
  private final CancellationRequestRepository requests;
  private final Clock clock;
  private final FullFundingService fullFunding;

  public OrderConfirmationService(
      OrderCommandLocks locks,
      OrderHistoryRepository histories,
      OrderEventPublisher events,
      OrderDetailQueries details,
      PaymentRepository payments,
      SettlementRepository settlements,
      CancellationRequestRepository requests,
      Clock clock,
      FullFundingService fullFunding) {
    this.locks = locks;
    this.histories = histories;
    this.events = events;
    this.details = details;
    this.payments = payments;
    this.settlements = settlements;
    this.requests = requests;
    this.clock = clock;
    this.fullFunding = fullFunding;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public OrderDetailResponse confirm(long memberId, String id) {
    long orderId =
        OrderIds.positive(id)
            .orElseThrow(() ->
                new BusinessException(
                    HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "주문 ID를 확인해 주세요."));
    var order = locks.lockParticipantOrder(memberId, orderId);
    if (order.isFullFunding()) {
      return fullFunding.confirm(memberId, orderId);
    }
    TradePolicyVersion.requireLegacyCommand(order.getPolicyVersion());
    if (!order.getBuyerId().equals(memberId))
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "구매자만 구매 확정할 수 있습니다.");
    boolean completed = order.getStatus() == OrderStatus.COMPLETED;
    if (!completed && order.getStatus() != OrderStatus.AWAITING_CONFIRMATION)
      throw new BusinessException(
          HttpStatus.CONFLICT, "ORDER_STATE_CONFLICT", "검수 대기 주문만 구매 확정할 수 있습니다.");
    if (!requests.findByOrderIdAndStatus(orderId, CancellationRequestStatus.PENDING).isEmpty())
      throw new BusinessException(
          HttpStatus.CONFLICT, "CANCELLATION_PENDING", "대기 중인 취소 요청을 먼저 처리해 주세요.");
    var payment = payments.findByOrderId(orderId).orElseThrow(OrderConfirmationService::integrity);
    if (payment.status() != PaymentStatus.PAID
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
      var at = UtcTimes.nowMicros(clock);
      order.confirm(at);
      settlements.saveAndFlush(Settlement.create(orderId, payment.amount(), payment.rate(), at));
      events.publish(order, histories.saveAndFlush(OrderHistory.completed(orderId, memberId, at)));
    }
    var detail = details.forParticipant(orderId, memberId);
    return detail.withAllowedActions(
        OrderAllowedActions.actions(
            detail.policyVersion(),
            detail.status(),
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
