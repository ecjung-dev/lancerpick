package com.example.lancerpick.admin.dto;

import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.wallet.entity.WalletTopupStatus;
import java.time.OffsetDateTime;
import java.util.Map;

/** 관리자 연결용 읽기 모델. Entity·결제 키·외부 응답·Batch 원문은 노출하지 않는다. */
public final class AdminFinanceResponses {
  private AdminFinanceResponses() {}

  public enum BlockReason {
    NONE,
    TERMINAL_STATE,
    PAYMENT_NOT_IDENTIFIED,
    TOO_EARLY,
    LEASE_ACTIVE,
    RETRY_DELAY,
    AUTOMATIC_LIMIT_REACHED,
    RECOVERY_DISABLED,
    PROVIDER_DISABLED,
    JOB_RUNNING,
    JOB_NOT_RESTARTABLE,
    NEWER_EXECUTION_EXISTS,
    INVALID_JOB_PARAMETERS
  }

  /** 조회 시점의 후보 판단이다. 실행 권한/예약을 부여하지 않으며 실제 실행은 재검사가 필요하다. */
  public record Decision(boolean eligible, BlockReason reason) {
    public static Decision ready() {
      return new Decision(true, BlockReason.NONE);
    }

    public static Decision blocked(BlockReason reason) {
      return new Decision(false, reason);
    }
  }

  public record Topup(
      String id,
      String memberId,
      long amount,
      WalletTopupStatus status,
      String lastErrorCode,
      int recoveryAttemptCount,
      OffsetDateTime createdAt,
      OffsetDateTime processingStartedAt,
      OffsetDateTime nextRecoveryAt,
      OffsetDateTime recoveryLeaseUntil,
      OffsetDateTime completedAt,
      Decision automaticRecovery,
      Decision manualRecheck) {}

  public record PaymentRecord(
      String id,
      String orderId,
      String buyerId,
      String sellerId,
      String policyVersion,
      OrderStatus orderStatus,
      String status,
      long amount,
      long refundedAmount,
      OffsetDateTime paidAt,
      OffsetDateTime refundedAt) {}

  public record Funds(
      String paymentId,
      String status,
      long amount,
      long remainingAmount,
      OffsetDateTime heldAt,
      OffsetDateTime resolvedAt) {}

  public record SettlementRecord(
      String id,
      String type,
      long grossAmount,
      int feeRateBps,
      long feeAmount,
      long sellerAmount,
      OffsetDateTime createdAt) {}

  public record Closure(
      String reason,
      long paidAmount,
      long buyerRefundAmount,
      long sellerGrossAmount,
      long feeAmount,
      long sellerNetAmount) {}

  /** 아직 결제하지 않은 주문도 조회한다. 누락된 금융 기록은 0원 대신 null로 구분한다. */
  public record OrderFinance(
      String orderId,
      String buyerId,
      String sellerId,
      String policyVersion,
      OrderStatus orderStatus,
      long orderAmount,
      PaymentRecord payment,
      Funds funds,
      SettlementRecord settlement,
      Closure closure) {}

  public record RecoveryJob(
      String executionId,
      String instanceId,
      String status,
      OffsetDateTime scanRequestedAt,
      long readCount,
      long creditedCount,
      long filteredCount,
      Map<String, Long> outcomes,
      Decision restart) {
    public RecoveryJob {
      outcomes = Map.copyOf(outcomes);
    }
  }
}
