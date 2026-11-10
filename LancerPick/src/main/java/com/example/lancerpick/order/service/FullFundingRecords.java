package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.contract.repository.ContractSignatureRepository;
import com.example.lancerpick.contract.repository.OrderContractRepository;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderClosure;
import com.example.lancerpick.order.repository.OrderClosureRepository;
import com.example.lancerpick.order.repository.OrderTradeTermsRepository;
import com.example.lancerpick.payment.entity.OrderFunds;
import com.example.lancerpick.payment.entity.OrderFundsStatus;
import com.example.lancerpick.payment.entity.Payment;
import com.example.lancerpick.payment.entity.PaymentStatus;
import com.example.lancerpick.payment.repository.OrderFundsRepository;
import com.example.lancerpick.payment.repository.PaymentRepository;
import com.example.lancerpick.payment.service.FullFundingPolicy;
import com.example.lancerpick.settlement.repository.SettlementRepository;
import com.example.lancerpick.wallet.entity.WalletEntryType;
import com.example.lancerpick.wallet.repository.WalletEntryRepository;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * V3 주문의 서명 계약·결제·보관금·원장·종료 기록 대사. {@link FullFundingService}의 트랜잭션 안에서 호출자가 주문 잠금을 얻은 뒤 호출하며 상태를
 * 바꾸지 않는다. 원장 확인은 기존과 같이 해당 원장 행을 잠근다(호출 위치·순서는 서비스가 정한다). 불일치는 500 {@code DATA_INTEGRITY_ERROR},
 * 서명 미완료만 409 {@code CONTRACT_NOT_SIGNED}다.
 */
@Component
@Profile("!demo")
@RequiredArgsConstructor
public class FullFundingRecords {
  private static final int FEE_RATE_BPS = FullFundingPolicy.FEE_RATE_BPS;

  private final OrderTradeTermsRepository orderTradeTermsRepository;
  private final OrderContractRepository orderContractRepository;
  private final ContractSignatureRepository contractSignatureRepository;
  private final PaymentRepository paymentRepository;
  private final OrderFundsRepository orderFundsRepository;
  private final WalletEntryRepository walletEntryRepository;
  private final SettlementRepository settlementRepository;
  private final OrderClosureRepository orderClosureRepository;

  void requireSignedContract(Order order) {
    var tradeTerms =
        orderTradeTermsRepository
            .findById(order.getId())
            .orElseThrow(FullFundingRecords::integrityError);
    if (!"FULL_HOLD".equals(tradeTerms.getFundingMode())) {
      throw integrityError();
    }
    var contract =
        orderContractRepository
            .findByOrderIdAndActiveSlot(order.getId(), 1)
            .orElseThrow(() -> contractNotSigned("계약 서명이 필요합니다."));
    var contractSignatures =
        contractSignatureRepository.findByContractIdOrderByIdAsc(contract.getId());
    if (!"SIGNED".equals(contract.getStatus()) || contractSignatures.size() != 2) {
      throw contractNotSigned("양측 계약 서명이 필요합니다.");
    }
    for (var signature : contractSignatures) {
      if ((signature.getMemberId() == order.getBuyerId()
              && !"BUYER".equals(signature.getPartyRole()))
          || (signature.getMemberId() == order.getSellerId()
              && !"SELLER".equals(signature.getPartyRole()))
          || !MessageDigest.isEqual(signature.getSignedContentHash(), contract.getContentHash())
          || !MessageDigest.isEqual(signature.getSignedFileHash(), contract.getFileHash())
          || !(signature.getMemberId() == order.getBuyerId()
              || signature.getMemberId() == order.getSellerId())) {
        throw integrityError();
      }
    }
  }

  Payment requirePayment(Order order) {
    var payment =
        paymentRepository
            .findByOrderId(order.getId())
            .orElseThrow(FullFundingRecords::integrityError);
    if (!TradePolicyVersion.CONTRACT_V3.name().equals(payment.getOrderPolicyVersion())
        || !"FULL".equals(payment.getInstallmentType())
        || payment.amount() != order.getPriceAmount()
        || payment.rate() != FEE_RATE_BPS
        || order.getPaidAt() == null
        || !order.getPaidAt().equals(payment.getPaidAt())) {
      throw integrityError();
    }
    return payment;
  }

  OrderFunds requireOrderFunds(Order order, Payment payment) {
    var orderFunds =
        orderFundsRepository
            .findById(order.getId())
            .orElseThrow(FullFundingRecords::integrityError);
    if (!orderFunds.getHeldAt().equals(payment.getPaidAt())
        || orderFunds.getPaymentId() != payment.id()
        || orderFunds.getAmount() != payment.amount()
        || (orderFunds.getStatus() == OrderFundsStatus.HELD
            ? orderFunds.getRemainingAmount() != payment.amount()
            : orderFunds.getRemainingAmount() != 0)) {
      throw integrityError();
    }
    return orderFunds;
  }

  void requirePaymentEntry(Order order, Payment payment) {
    requireWalletEntry(
        order.getBuyerId(), payment, WalletEntryType.PAYMENT, -payment.amount(), "PAYMENT:");
  }

  void requireRefundEntry(Order order, Payment payment) {
    requireWalletEntry(
        order.getBuyerId(), payment, WalletEntryType.REFUND, payment.amount(), "REFUND:");
  }

  void requireCompletedRecords(Order order, Payment payment, OrderFunds orderFunds) {
    var closure =
        orderClosureRepository
            .findByOrderId(order.getId())
            .orElseThrow(FullFundingRecords::integrityError);
    var settlement =
        settlementRepository
            .findByOrderId(order.getId())
            .orElseThrow(FullFundingRecords::integrityError);
    var distribution = FullFundingPolicy.completed(payment.amount());
    if (payment.status() != PaymentStatus.PAID
        || orderFunds.getStatus() != OrderFundsStatus.RELEASED
        || !isCompletionClosure(order, closure)
        || !"NORMAL".equals(settlement.getSettlementType())
        || closure.getFeeAmount() != distribution.fee()
        || closure.getSellerNetAmount() != distribution.sellerPayout()
        || closure.getBuyerRefundAmount() != 0
        || !Arrays.equals(
            settlement.amounts(),
            new long[] {
              payment.amount(), FEE_RATE_BPS, distribution.fee(), distribution.sellerPayout()
            })) {
      throw integrityError();
    }
    requireWalletEntry(
        order.getSellerId(),
        payment,
        WalletEntryType.SELLER_PAYOUT,
        distribution.sellerPayout(),
        "SELLER_PAYOUT:");
  }

  private void requireWalletEntry(
      long memberId, Payment payment, WalletEntryType type, long amount, String prefix) {
    var walletEntry =
        walletEntryRepository
            .findByEventKeyForUpdate(prefix + payment.id())
            .orElseThrow(FullFundingRecords::integrityError);
    if (walletEntry.getMemberId() != memberId
        || walletEntry.getPaymentId() == null
        || walletEntry.getPaymentId() != payment.id()
        || walletEntry.getType() != type
        || walletEntry.getAmountDelta() != amount) {
      throw integrityError();
    }
  }

  private static boolean isCompletionClosure(Order order, OrderClosure closure) {
    if ("COMPLETED".equals(closure.getReason()))
      return "MEMBER".equals(closure.getActorType())
          && Objects.equals(closure.getActorId(), order.getBuyerId());
    return "AUTO_CONFIRMED".equals(closure.getReason())
        && "SYSTEM".equals(closure.getActorType())
        && closure.getActorId() == null
        && closure.getSourceTimerId() != null;
  }

  private static BusinessException contractNotSigned(String message) {
    return new BusinessException(HttpStatus.CONFLICT, "CONTRACT_NOT_SIGNED", message);
  }

  static BusinessException integrityError() {
    return new BusinessException(
        HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY_ERROR", "거래 금액 기록을 확인해야 합니다.");
  }
}
