package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.notification.service.OrderEventPublisher;
import com.example.lancerpick.order.dto.OrderDetailResponse;
import com.example.lancerpick.order.entity.OrderHistory;
import com.example.lancerpick.order.entity.OrderStatus;
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

@Service
@Profile("!demo")
public class OrderPaymentService {
  private final OrderCommandLocks locks;
  private final OrderHistoryRepository histories;
  private final OrderEventPublisher events;
  private final OrderDetailQueries details;
  private final PaymentRepository payments;
  private final WalletRepository wallets;
  private final WalletEntryRepository entries;
  private final Clock clock;
  private final FullFundingService fullFunding;

  public OrderPaymentService(
      OrderCommandLocks locks,
      OrderHistoryRepository histories,
      OrderEventPublisher events,
      OrderDetailQueries details,
      PaymentRepository payments,
      WalletRepository wallets,
      WalletEntryRepository entries,
      Clock clock,
      FullFundingService fullFunding) {
    this.locks = locks;
    this.histories = histories;
    this.events = events;
    this.details = details;
    this.payments = payments;
    this.wallets = wallets;
    this.entries = entries;
    this.clock = clock;
    this.fullFunding = fullFunding;
  }

  /** 회원 공유 잠금 → 주문 → 구매자 지갑. 외부 결제 통신은 하지 않는다. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public OrderDetailResponse pay(long memberId, String id, boolean agreed) {
    long orderId = OrderIds.positive(id).orElseThrow(OrderPaymentService::invalid);
    if (!agreed) throw invalid();
    var order = locks.lockParticipantOrder(memberId, orderId);
    if (order.isFullFunding()) {
      return fullFunding.pay(memberId, orderId);
    }
    if (!order.getBuyerId().equals(memberId))
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "구매자만 결제할 수 있습니다.");
    if (!TradePolicyVersion.LEGACY_V1.name().equals(order.getPolicyVersion())) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "CONTRACT_PAYMENT_NOT_READY", "계약 주문의 결제 기능이 아직 준비되지 않았습니다.");
    }
    var existing = payments.findByOrderId(orderId);
    if (existing.isPresent()) {
      var payment = existing.get();
      if (payment.status() == PaymentStatus.REFUNDED)
        throw new BusinessException(
            HttpStatus.CONFLICT, "PAYMENT_ALREADY_REFUNDED", "이미 환불된 결제입니다.");
      if (payment.status() != PaymentStatus.PAID
          || payment.amount() != order.getPriceAmount()
          || !Set.of(
                  OrderStatus.IN_PROGRESS,
                  OrderStatus.AWAITING_CONFIRMATION,
                  OrderStatus.REVISION_IN_PROGRESS,
                  OrderStatus.COMPLETED)
              .contains(order.getStatus())) throw integrity();
      var entry =
          entries
              .findByEventKeyForUpdate("PAYMENT:" + payment.id())
              .orElseThrow(OrderPaymentService::integrity);
      if (entry.getMemberId() != memberId
          || entry.getPaymentId() == null
          || entry.getPaymentId() != payment.id()
          || entry.getType() != WalletEntryType.PAYMENT
          || entry.getAmountDelta() != -payment.amount()) throw integrity();
      return response(orderId, memberId);
    }
    if (Set.of(
            OrderStatus.IN_PROGRESS,
            OrderStatus.AWAITING_CONFIRMATION,
            OrderStatus.REVISION_IN_PROGRESS,
            OrderStatus.COMPLETED)
        .contains(order.getStatus())) throw integrity();
    if (order.getStatus() != OrderStatus.AWAITING_PAYMENT)
      throw new BusinessException(
          HttpStatus.CONFLICT, "ORDER_STATE_CONFLICT", "결제 대기 주문만 결제할 수 있습니다.");
    var wallet =
        wallets.findByMemberIdForUpdate(memberId).orElseThrow(OrderPaymentService::integrity);
    var at = UtcTimes.nowMicros(clock);
    order.pay(at);
    wallet.debit(order.getPriceAmount(), at);
    long paymentId = payments.insert(orderId, order.getPriceAmount(), at);
    entries.saveAndFlush(
        WalletEntry.payment(memberId, paymentId, order.getPriceAmount(), wallet.getBalance(), at));
    events.publish(order, histories.saveAndFlush(OrderHistory.paid(orderId, memberId, at)));
    return response(orderId, memberId);
  }

  private OrderDetailResponse response(long id, long memberId) {
    var detail = details.forParticipant(id, memberId);
    return detail.withAllowedActions(
        OrderAllowedActions.actions(
            detail.policyVersion(),
            detail.status(),
            true,
            detail.revisionRequest(),
            detail.cancellationRequests(),
            detail.review()));
  }

  private static BusinessException invalid() {
    return new BusinessException(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "주문 ID와 조건 동의를 확인해 주세요.");
  }

  private static BusinessException integrity() {
    return new BusinessException(
        HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY_ERROR", "결제 기록을 확인해야 합니다.");
  }
}
