package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.notification.service.OrderEventPublisher;
import com.example.lancerpick.order.dto.OrderDetailResponse;
import com.example.lancerpick.order.dto.OrderFundsResponse;
import com.example.lancerpick.order.entity.CancellationRequest;
import com.example.lancerpick.order.entity.CancellationRequestStatus;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderClosure;
import com.example.lancerpick.order.entity.OrderHistory;
import com.example.lancerpick.order.entity.OrderHistoryAction;
import com.example.lancerpick.order.entity.OrderResponseTimer;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.repository.CancellationRequestRepository;
import com.example.lancerpick.order.repository.DeliveryRepository;
import com.example.lancerpick.order.repository.OrderClosureRepository;
import com.example.lancerpick.order.repository.OrderDetailQueries;
import com.example.lancerpick.order.repository.OrderHistoryRepository;
import com.example.lancerpick.order.repository.OrderRepository;
import com.example.lancerpick.payment.entity.OrderFunds;
import com.example.lancerpick.payment.entity.OrderFundsStatus;
import com.example.lancerpick.payment.entity.Payment;
import com.example.lancerpick.payment.entity.PaymentStatus;
import com.example.lancerpick.payment.repository.OrderFundsRepository;
import com.example.lancerpick.payment.repository.PaymentRepository;
import com.example.lancerpick.payment.service.FullFundingPolicy;
import com.example.lancerpick.settlement.entity.Settlement;
import com.example.lancerpick.settlement.repository.SettlementRepository;
import com.example.lancerpick.wallet.entity.Wallet;
import com.example.lancerpick.wallet.entity.WalletEntry;
import com.example.lancerpick.wallet.repository.WalletEntryRepository;
import com.example.lancerpick.wallet.repository.WalletRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * V3 주문의 결제 금액 보관·수동 구매 확정 지급·승인 환불을 조율한다. 실행 회원 공유 잠금 → 주문 배타 잠금 → 변경 대상 지갑 잠금 순서를 유지하며 외부 통신을
 * 포함하지 않는다. 계약·결제·보관금·원장·종료 기록 대사는 {@link FullFundingRecords}가 이 트랜잭션 안에서 맡는다.
 */
@Service
@Profile("!demo")
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class FullFundingService {
  private static final int FEE_RATE_BPS = FullFundingPolicy.FEE_RATE_BPS;

  private final OrderRepository orderRepository;
  private final PaymentRepository paymentRepository;
  private final OrderFundsRepository orderFundsRepository;
  private final WalletRepository walletRepository;
  private final WalletEntryRepository walletEntryRepository;
  private final SettlementRepository settlementRepository;
  private final OrderClosureRepository orderClosureRepository;
  private final CancellationRequestRepository cancellationRequestRepository;
  private final DeliveryRepository deliveryRepository;
  private final ReviewTimers reviewTimers;
  private final FullFundingRecords records;
  private final OrderHistoryRepository orderHistoryRepository;
  private final OrderEventPublisher orderEventPublisher;
  private final OrderDetailQueries orderDetailQueries;
  private final DeadlineNotices deadlineNotices;
  private final OrderCommandLocks commandLocks;
  private final Clock clock;

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public OrderFundsResponse summary(long memberId, long orderId) {
    var order =
        orderRepository
            .findById(orderId)
            .filter(candidate ->
                (candidate.getBuyerId() == memberId || candidate.getSellerId() == memberId)
                    && candidate.isFullFunding())
            .orElseThrow(() ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "주문을 찾을 수 없습니다."));
    if (paymentRepository.findByOrderId(orderId).isEmpty()) {
      return new OrderFundsResponse(
          "NOT_PAID", order.getPriceAmount(), 0, 0, null, null, null, null);
    }
    var payment = records.requirePayment(order);
    var orderFunds = records.requireOrderFunds(order, payment);
    var closure = orderClosureRepository.findByOrderId(orderId).orElse(null);
    if ((orderFunds.getStatus() == OrderFundsStatus.HELD) != (closure == null)) {
      throw integrityError();
    }
    return new OrderFundsResponse(
        orderFunds.getStatus().name(),
        orderFunds.getAmount(),
        orderFunds.getRemainingAmount(),
        payment.getRefundedAmount(),
        closure == null ? null : closure.getFeeAmount(),
        closure == null ? null : closure.getSellerNetAmount(),
        payment.getPaidAt().atOffset(ZoneOffset.UTC),
        orderFunds.getResolvedAt() == null
            ? null
            : orderFunds.getResolvedAt().atOffset(ZoneOffset.UTC));
  }

  public OrderDetailResponse pay(long memberId, long orderId) {
    var order = lockOrder(memberId, orderId);
    requireRole(order.getBuyerId() == memberId);
    if (paymentRepository.findByOrderId(orderId).isPresent()) {
      var payment = records.requirePayment(order);
      var orderFunds = records.requireOrderFunds(order, payment);
      records.requirePaymentEntry(order, payment);
      if (payment.status() == PaymentStatus.REFUNDED) {
        throw conflict("PAYMENT_ALREADY_REFUNDED", "이미 반환된 결제입니다.");
      }
      if (!(order.getStatus().isWorkOpen() || order.getStatus() == OrderStatus.COMPLETED)) {
        throw integrityError();
      }
      if (order.getStatus() == OrderStatus.COMPLETED) {
        records.requireCompletedRecords(order, payment, orderFunds);
      } else if (orderFunds.getStatus() != OrderFundsStatus.HELD
          || orderClosureRepository.findByOrderId(orderId).isPresent()
          || settlementRepository.findByOrderId(orderId).isPresent()) {
        throw integrityError();
      }
      return orderResponse(order, memberId);
    }
    if (order.getStatus() != OrderStatus.AWAITING_PAYMENT
        || order.getPaidAt() != null
        || orderFundsRepository.existsById(orderId)
        || orderClosureRepository.findByOrderId(orderId).isPresent()
        || settlementRepository.findByOrderId(orderId).isPresent()) {
      throw invalidState();
    }
    records.requireSignedContract(order);
    requireNoPendingCancellation(order);
    reviewTimers.requireNoActive(order);
    var wallet =
        walletRepository
            .findByMemberIdForUpdate(memberId)
            .orElseThrow(FullFundingService::integrityError);
    var occurredAt = currentTime();
    order.pay(occurredAt);
    wallet.debit(order.getPriceAmount(), occurredAt);
    var payment =
        paymentRepository.saveAndFlush(
            Payment.fullFunding(orderId, order.getPriceAmount(), occurredAt));
    orderFundsRepository.saveAndFlush(
        OrderFunds.hold(orderId, payment.id(), payment.amount(), occurredAt));
    walletEntryRepository.saveAndFlush(
        WalletEntry.payment(
            memberId, payment.id(), payment.amount(), wallet.getBalance(), occurredAt));
    orderEventPublisher.publish(
        order,
        orderHistoryRepository.saveAndFlush(OrderHistory.paid(orderId, memberId, occurredAt)));
    deadlineNotices.deliveryDue(order, payment.id());
    return orderResponse(order, memberId);
  }

  public OrderDetailResponse confirm(long memberId, long orderId) {
    var order = lockOrder(memberId, orderId);
    requireRole(order.getBuyerId() == memberId);
    // 인증 필터를 통과한 뒤 보안 잠금이 커밋된 확정 요청이 판매자 지급까지 가지 않게 회원 잠금 아래에서 다시 본다.
    commandLocks.requireNotSecurityLocked(memberId);
    var payment = records.requirePayment(order);
    var orderFunds = records.requireOrderFunds(order, payment);
    records.requirePaymentEntry(order, payment);
    if (order.getStatus() == OrderStatus.COMPLETED) {
      records.requireCompletedRecords(order, payment, orderFunds);
      return orderResponse(order, memberId);
    }
    if (order.getStatus() != OrderStatus.AWAITING_CONFIRMATION
        || payment.status() != PaymentStatus.PAID
        || orderFunds.getStatus() != OrderFundsStatus.HELD) {
      throw invalidState();
    }
    requireNoPendingCancellation(order);
    if (!deliveryRepository.existsByOrderIdAndSequenceNo(orderId, 1)
        || (deliveryRepository.revisions(orderId) > 0
            && !deliveryRepository.existsByOrderIdAndSequenceNo(orderId, 2))) {
      throw integrityError();
    }
    if (orderClosureRepository.findByOrderId(orderId).isPresent()
        || settlementRepository.findByOrderId(orderId).isPresent()) {
      throw integrityError();
    }
    var wallet =
        walletRepository
            .findByMemberIdForUpdate(order.getSellerId())
            .orElseThrow(FullFundingService::integrityError);
    // 지갑 잠금 대기 중 다른 거래가 지급할 수 있으므로, 잠금 뒤의 시각으로 지급·만료를 함께 판단한다.
    var occurredAt = currentTime();
    // 구매자 확정은 활성 자동 확정 타이머를 끝낸다. 만료 시각이 지났으면 자동 확정이 우선이라 충돌로 응답한다.
    reviewTimers.closeForBuyerResponse(order, memberId, occurredAt, ReviewTimers.ORDER_CLOSED);
    payoutToSeller(order, payment, wallet, occurredAt);
    orderClosureRepository.saveAndFlush(
        OrderClosure.completed(orderId, memberId, payment.amount(), occurredAt));
    orderFunds.release(occurredAt);
    order.confirm(occurredAt);
    orderRepository.flush();
    orderFundsRepository.flush();
    orderEventPublisher.publish(
        order,
        orderHistoryRepository.saveAndFlush(OrderHistory.completed(orderId, memberId, occurredAt)));
    return orderResponse(order, memberId);
  }

  public OrderDetailResponse refund(long memberId, long orderId, String reason) {
    var order = lockOrder(memberId, orderId);
    requireRole(order.getSellerId() == memberId);
    var payment = records.requirePayment(order);
    var orderFunds = records.requireOrderFunds(order, payment);
    records.requirePaymentEntry(order, payment);
    if (order.getStatus() == OrderStatus.CANCELLED) {
      var closure =
          orderClosureRepository
              .findByOrderId(orderId)
              .orElseThrow(FullFundingService::integrityError);
      // 관리자 승인으로 이미 취소된 주문은 판매자 승인의 재전송이 아니다.
      if ("ADMIN_CANCELLATION".equals(closure.getReason())) throw invalidState();
      if (payment.status() != PaymentStatus.REFUNDED
          || orderFunds.getStatus() != OrderFundsStatus.REFUNDED
          || !"MUTUAL_CANCELLATION".equals(closure.getReason())
          || closure.getBuyerRefundAmount() != payment.amount()
          || settlementRepository.findByOrderId(orderId).isPresent()) {
        throw integrityError();
      }
      records.requireRefundEntry(order, payment);
      return orderResponse(order, memberId);
    }
    if (!order.getStatus().isWorkOpen()
        || payment.status() != PaymentStatus.PAID
        || orderFunds.getStatus() != OrderFundsStatus.HELD) {
      throw invalidState();
    }
    var pendingRequests =
        cancellationRequestRepository.findByOrderIdAndStatus(
            orderId, CancellationRequestStatus.PENDING);
    if (pendingRequests.size() != 1) {
      throw conflict("CANCELLATION_REQUEST_REQUIRED", "구매자의 취소 요청을 승인해 주세요.");
    }
    var request = pendingRequests.getFirst();
    if (request.getRequestedBy() != order.getBuyerId()) {
      throw integrityError();
    }
    reviewTimers.requireNoActive(order);
    if (orderClosureRepository.findByOrderId(orderId).isPresent()
        || settlementRepository.findByOrderId(orderId).isPresent()) {
      throw integrityError();
    }
    var wallet =
        walletRepository
            .findByMemberIdForUpdate(order.getBuyerId())
            .orElseThrow(FullFundingService::integrityError);
    var occurredAt = currentTime();
    request.approve(memberId, null, occurredAt);
    refundHeldFunds(order, payment, orderFunds, wallet, occurredAt);
    orderClosureRepository.saveAndFlush(
        OrderClosure.refunded(orderId, memberId, request.getId(), payment.amount(), occurredAt));
    cancelAndPublish(order, memberId, occurredAt, null, reason);
    return orderResponse(order, memberId);
  }

  /**
   * 운영 문의(취소 검토)에 대한 관리자 승인 취소(K4). 호출자가 관리자 공유 잠금 → 주문 배타 잠금 → 문의 잠금을 얻고 대기 취소 요청·문의를 확인한 같은
   * 트랜잭션에서 호출한다. 판매자 승인과 같은 전액 반환·원장을 남기고, 취소 요청은 ADMIN 처리로, 종료 기록은 ADMIN_CANCELLATION(문의 ID 포함)으로
   * 구분한다. 처리 시각은 구매자 지갑 잠금 뒤 {@code timeAfterWalletLock}으로 정하고 돌려준다. 호출자는 문의 종료에 같은 시각을 쓴다.
   */
  public LocalDateTime adminRefund(
      Order order,
      CancellationRequest request,
      long adminId,
      long inquiryId,
      String reason,
      Supplier<LocalDateTime> timeAfterWalletLock) {
    long orderId = order.getId();
    if (!order.isFullFunding()
        || request.getOrderId() != orderId
        || request.getRequestedBy() != order.getBuyerId()
        || request.getStatus() != CancellationRequestStatus.PENDING) {
      throw integrityError();
    }
    var payment = records.requirePayment(order);
    var orderFunds = records.requireOrderFunds(order, payment);
    records.requirePaymentEntry(order, payment);
    if (!order.getStatus().isWorkOpen()
        || payment.status() != PaymentStatus.PAID
        || orderFunds.getStatus() != OrderFundsStatus.HELD) {
      throw invalidState();
    }
    // 취소 요청 접수 시 활성 타이머는 이미 끝났다. 남아 있으면 기록 불일치다.
    reviewTimers.requireNoActive(order);
    if (orderClosureRepository.findByOrderId(orderId).isPresent()
        || settlementRepository.findByOrderId(orderId).isPresent()) {
      throw integrityError();
    }
    var wallet =
        walletRepository
            .findByMemberIdForUpdate(order.getBuyerId())
            .orElseThrow(FullFundingService::integrityError);
    // 지갑 잠금 대기 중 같은 구매자의 다른 거래가 지갑을 갱신했을 수 있으므로 잠금 뒤의 시각을 쓴다.
    var occurredAt = timeAfterWalletLock.get();
    request.resolveByAdmin(CancellationRequestStatus.APPROVED, adminId, reason, occurredAt);
    refundHeldFunds(order, payment, orderFunds, wallet, occurredAt);
    orderClosureRepository.saveAndFlush(
        OrderClosure.adminCancelled(
            orderId, adminId, request.getId(), inquiryId, payment.amount(), occurredAt));
    cancelAndPublish(order, adminId, occurredAt, reason, reason);
    return occurredAt;
  }

  /**
   * 만료된 자동 구매 확정 타이머로 시스템이 구매를 확정한다. 호출자가 주문 배타 잠금과 타이머 만료를 확인한 같은 트랜잭션에서 호출한다. 수동 확정과 같은 금액·원장·정산을
   * 남기고 종료 기록은 SYSTEM/AUTO_CONFIRMED로 구분한다.
   */
  public void autoConfirm(Order order, OrderResponseTimer timer, LocalDateTime occurredAt) {
    if (!order.isFullFunding() || timer.getOrderId() != order.getId() || !timer.isDue(occurredAt)) {
      throw integrityError();
    }
    long orderId = order.getId();
    var payment = records.requirePayment(order);
    var orderFunds = records.requireOrderFunds(order, payment);
    records.requirePaymentEntry(order, payment);
    if (order.getStatus() != OrderStatus.AWAITING_CONFIRMATION
        || payment.status() != PaymentStatus.PAID
        || orderFunds.getStatus() != OrderFundsStatus.HELD
        || !cancellationRequestRepository
            .findByOrderIdAndStatus(orderId, CancellationRequestStatus.PENDING)
            .isEmpty()
        || deliveryRepository
            .findFirstByOrderIdOrderBySequenceNoDesc(orderId)
            .map(delivery -> delivery.getId() != timer.getDeliveryId())
            .orElse(true)
        || orderClosureRepository.findByOrderId(orderId).isPresent()
        || settlementRepository.findByOrderId(orderId).isPresent()) {
      throw integrityError();
    }
    var wallet =
        walletRepository
            .findByMemberIdForUpdate(order.getSellerId())
            .orElseThrow(FullFundingService::integrityError);
    // 처리 대상 선정 시각을 지급에 재사용하지 않는다. 다른 주문이 먼저 지갑을 갱신했을 수 있다.
    occurredAt = currentTime();
    if (!timer.isDue(occurredAt)) throw integrityError();
    payoutToSeller(order, payment, wallet, occurredAt);
    orderClosureRepository.saveAndFlush(
        OrderClosure.autoConfirmed(orderId, timer.getId(), payment.amount(), occurredAt));
    timer.expire(occurredAt);
    orderFunds.release(occurredAt);
    order.confirm(occurredAt);
    orderRepository.flush();
    orderFundsRepository.flush();
    orderEventPublisher.publish(
        order,
        orderHistoryRepository.saveAndFlush(
            OrderHistory.autoConfirmed(orderId, timer.getId(), occurredAt)));
  }

  /** 판매자 지갑 잠금 뒤 호출한다. 수수료를 뺀 금액을 적립하고 원장·정산을 남긴다. */
  private void payoutToSeller(Order order, Payment payment, Wallet wallet, LocalDateTime occurredAt) {
    var distribution = FullFundingPolicy.completed(payment.amount());
    wallet.creditSettlement(distribution.sellerPayout(), occurredAt);
    walletEntryRepository.saveAndFlush(
        WalletEntry.sellerPayout(
            order.getSellerId(),
            payment.id(),
            distribution.sellerPayout(),
            wallet.getBalance(),
            occurredAt));
    settlementRepository.saveAndFlush(
        Settlement.create(order.getId(), payment.amount(), FEE_RATE_BPS, occurredAt));
  }

  /** 구매자 지갑 잠금 뒤 호출한다. 보관금 전액을 반환하고 원장을 남긴다. */
  private void refundHeldFunds(
      Order order, Payment payment, OrderFunds orderFunds, Wallet wallet, LocalDateTime occurredAt) {
    wallet.refund(payment.amount(), occurredAt);
    payment.refund(occurredAt);
    orderFunds.refund(occurredAt);
    walletEntryRepository.saveAndFlush(
        WalletEntry.refund(
            order.getBuyerId(), payment.id(), payment.amount(), wallet.getBalance(), occurredAt));
  }

  /** 주문을 취소 상태로 바꾸고 취소 승인·취소 이력을 남긴다. 승인 이력 사유와 취소 사유는 호출 흐름마다 다르다. */
  private void cancelAndPublish(
      Order order,
      long actorId,
      LocalDateTime occurredAt,
      String approvalReason,
      String cancelReason) {
    var previousStatus = order.getStatus();
    order.cancel(occurredAt);
    orderRepository.flush();
    paymentRepository.flush();
    orderFundsRepository.flush();
    cancellationRequestRepository.flush();
    orderEventPublisher.publish(
        order,
        orderHistoryRepository.saveAndFlush(
            OrderHistory.cancellationRequestEvent(
                order.getId(),
                actorId,
                OrderHistoryAction.CANCELLATION_APPROVED,
                previousStatus,
                occurredAt,
                approvalReason)));
    orderEventPublisher.publish(
        order,
        orderHistoryRepository.saveAndFlush(
            OrderHistory.cancelled(order.getId(), actorId, previousStatus, occurredAt, cancelReason)));
  }

  // public 진입점이 단독 호출돼도 잠금 순서와 V3·금액 검증을 보장한다. 호출 서비스가 이미 잠갔다면 같은 트랜잭션의 재잠금이다.
  private Order lockOrder(long memberId, long orderId) {
    var order = commandLocks.lockParticipantOrder(memberId, orderId);
    if (!order.isFullFunding()) {
      throw conflict("ORDER_POLICY_NOT_READY", "결제 금액 보관 정책 주문이 아닙니다.");
    }
    FullFundingPolicy.requireAmount(order.getPriceAmount());
    return order;
  }

  private void requireNoPendingCancellation(Order order) {
    if (!cancellationRequestRepository
        .findByOrderIdAndStatus(order.getId(), CancellationRequestStatus.PENDING)
        .isEmpty()) {
      throw conflict("CANCELLATION_PENDING", "대기 중인 취소 요청을 먼저 처리해 주세요.");
    }
  }

  private LocalDateTime currentTime() {
    return UtcTimes.nowMicros(clock);
  }

  private OrderDetailResponse orderResponse(Order order, long memberId) {
    var detail = orderDetailQueries.forParticipant(order.getId(), memberId);
    return OrderAllowedActions.attach(detail, order.getBuyerId() == memberId);
  }

  private static void requireRole(boolean allowed) {
    if (!allowed) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "처리 권한이 없습니다.");
    }
  }

  private static BusinessException conflict(String code, String message) {
    return new BusinessException(HttpStatus.CONFLICT, code, message);
  }

  private static BusinessException invalidState() {
    return conflict("ORDER_STATE_CONFLICT", "현재 주문 상태에서 처리할 수 없습니다.");
  }

  private static BusinessException integrityError() {
    return FullFundingRecords.integrityError();
  }
}
