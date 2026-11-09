package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.common.text.TextNormalizer;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.notification.service.OrderEventPublisher;
import com.example.lancerpick.order.dto.OrderDetailResponse;
import com.example.lancerpick.order.entity.CancellationRequestStatus;
import com.example.lancerpick.order.entity.OrderHistory;
import com.example.lancerpick.order.entity.OrderHistoryAction;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.repository.CancellationRequestRepository;
import com.example.lancerpick.order.repository.OrderDetailQueries;
import com.example.lancerpick.order.repository.OrderHistoryRepository;
import com.example.lancerpick.payment.entity.PaymentStatus;
import com.example.lancerpick.payment.repository.PaymentRepository;
import com.example.lancerpick.wallet.entity.WalletEntry;
import com.example.lancerpick.wallet.entity.WalletEntryType;
import com.example.lancerpick.wallet.repository.WalletEntryRepository;
import com.example.lancerpick.wallet.repository.WalletRepository;
import java.time.Clock;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 직접 취소 진입점. 취소 요청 제출·승인·거절·철회 API는 주문 담당자의 후속 작업이다. */
@Service
@Profile("!demo")
public class OrderCancellationService {
  private final OrderCommandLocks locks;
  private final OrderHistoryRepository histories;
  private final OrderEventPublisher events;
  private final OrderDetailQueries details;
  private final PaymentRepository payments;
  private final WalletRepository wallets;
  private final WalletEntryRepository entries;
  private final CancellationRequestRepository requests;
  private final Clock clock;
  private final FullFundingService fullFunding;

  public OrderCancellationService(
      OrderCommandLocks locks,
      OrderHistoryRepository histories,
      OrderEventPublisher events,
      OrderDetailQueries details,
      PaymentRepository payments,
      WalletRepository wallets,
      WalletEntryRepository entries,
      CancellationRequestRepository requests,
      Clock clock,
      FullFundingService fullFunding) {
    this.locks = locks;
    this.histories = histories;
    this.events = events;
    this.details = details;
    this.payments = payments;
    this.wallets = wallets;
    this.entries = entries;
    this.requests = requests;
    this.clock = clock;
    this.fullFunding = fullFunding;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public OrderDetailResponse cancel(long memberId, String id, String rawReason) {
    long orderId = OrderIds.positive(id).orElseThrow(OrderCancellationService::invalid);
    if (rawReason == null) throw invalid();
    String reason = TextNormalizer.normalize(rawReason);
    if (reason.isEmpty() || reason.codePointCount(0, reason.length()) > 1000) throw invalid();
    var order = locks.lockParticipantOrder(memberId, orderId);
    if (order.isFullFunding() && order.getPaidAt() != null) {
      return fullFunding.refund(memberId, orderId, reason);
    }
    // 계약 주문은 선금 전(금액 이동 없음)만 이 경로로 취소한다. 선금 후 취소·반환은 전용 경로(E6)다.
    if (!order.isContractPolicy() || order.getPaidAt() != null)
      TradePolicyVersion.requireLegacyCommand(order.getPolicyVersion());
    boolean buyer = order.getBuyerId().equals(memberId);
    var payment = payments.findByOrderId(orderId);
    boolean paid = order.getPaidAt() != null;
    if (buyer && paid)
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "결제 후에는 취소 요청을 제출해 주세요.");
    if (order.getStatus() == OrderStatus.CANCELLED) {
      if (paid) {
        var original = payment.orElseThrow(OrderCancellationService::integrity);
        if (original.status() != PaymentStatus.REFUNDED || original.amount() != order.getPriceAmount())
          throw integrity();
        var refund =
            entries
                .findByEventKeyForUpdate("REFUND:" + original.id())
                .orElseThrow(OrderCancellationService::integrity);
        if (refund.getMemberId().longValue() != order.getBuyerId()
            || refund.getPaymentId() == null
            || refund.getPaymentId() != original.id()
            || refund.getType() != WalletEntryType.REFUND
            || refund.getAmountDelta() != original.amount()) throw integrity();
      } else if (payment.isPresent()) throw integrity();
      return response(orderId, memberId, buyer);
    }
    if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.REJECTED)
      throw state();
    if (!buyer && order.getStatus() == OrderStatus.REQUESTED) throw state();
    if (paid
        != Set.of(
                OrderStatus.IN_PROGRESS,
                OrderStatus.AWAITING_CONFIRMATION,
                OrderStatus.REVISION_IN_PROGRESS)
            .contains(order.getStatus())) throw integrity();
    var at = UtcTimes.nowMicros(clock);
    var from = order.getStatus();
    order.cancel(at);
    if (paid) {
      var original = payment.orElseThrow(OrderCancellationService::integrity);
      if (original.status() != PaymentStatus.PAID || original.amount() != order.getPriceAmount())
        throw integrity();
      var wallet =
          wallets
              .findByMemberIdForUpdate(order.getBuyerId())
              .orElseThrow(OrderCancellationService::integrity);
      var debit =
          entries
              .findByEventKeyForUpdate("PAYMENT:" + original.id())
              .orElseThrow(OrderCancellationService::integrity);
      if (debit.getMemberId().longValue() != order.getBuyerId()
          || debit.getPaymentId() == null
          || debit.getPaymentId() != original.id()
          || debit.getType() != WalletEntryType.PAYMENT
          || debit.getAmountDelta() != -original.amount()) throw integrity();
      if (entries.existsByEventKey("REFUND:" + original.id())) throw integrity();
      wallet.refund(original.amount(), at);
      if (payments.markRefunded(original.id(), at) != 1) throw integrity();
      entries.saveAndFlush(
          WalletEntry.refund(
              order.getBuyerId(), original.id(), original.amount(), wallet.getBalance(), at));
    } else if (payment.isPresent()) throw integrity();
    var pending = requests.findByOrderIdAndStatus(orderId, CancellationRequestStatus.PENDING);
    if (pending.size() > 1 || (!paid && !pending.isEmpty())) throw integrity();
    if (!pending.isEmpty()) {
      var request = pending.getFirst();
      request.approve(memberId, reason, at);
      requests.saveAndFlush(request);
      events.publish(
          order,
          histories.saveAndFlush(
              OrderHistory.cancellationRequestEvent(
                  orderId, memberId, OrderHistoryAction.CANCELLATION_APPROVED, from, at, reason)));
    }
    events.publish(
        order, histories.saveAndFlush(OrderHistory.cancelled(orderId, memberId, from, at, reason)));
    return response(orderId, memberId, buyer);
  }

  private OrderDetailResponse response(long id, long member, boolean buyer) {
    var d = details.forParticipant(id, member);
    return d.withAllowedActions(
        OrderAllowedActions.actions(
            d.policyVersion(),
            d.status(),
            buyer,
            d.revisionRequest(),
            d.cancellationRequests(),
            d.review()));
  }

  private static BusinessException invalid() {
    return new BusinessException(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "주문 ID와 취소 사유를 확인해 주세요.");
  }

  private static BusinessException integrity() {
    return new BusinessException(
        HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY_ERROR", "취소·환불 기록을 확인해야 합니다.");
  }

  private static BusinessException state() {
    return new BusinessException(HttpStatus.CONFLICT, "ORDER_STATE_CONFLICT", "취소할 수 없는 주문 상태입니다.");
  }
}
