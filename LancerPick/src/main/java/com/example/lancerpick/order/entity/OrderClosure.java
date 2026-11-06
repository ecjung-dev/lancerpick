package com.example.lancerpick.order.entity;

import com.example.lancerpick.payment.service.FullFundingPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 주문 종료 시 결제 금액의 반환·판매자 지급·수수료 분배를 변경 불가능한 기록으로 보존한다. */
@Getter
@Entity
@Table(name = "order_closures")
public class OrderClosure {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false, updatable = false)
  private long orderId;

  @Column(nullable = false, updatable = false, length = 40)
  private String reason;

  @Column(name = "source_timer_id", updatable = false)
  private Long sourceTimerId;

  @Column(name = "source_cancellation_request_id", updatable = false)
  private Long sourceCancellationRequestId;

  @Column(name = "inquiry_id", updatable = false)
  private Long inquiryId;

  @Column(name = "actor_type", nullable = false, updatable = false, length = 10)
  private String actorType;

  @Column(name = "actor_id", updatable = false)
  private Long actorId;

  @Column(name = "paid_amount", nullable = false, updatable = false)
  private long paidAmount;

  @Column(name = "buyer_refund_amount", nullable = false, updatable = false)
  private long buyerRefundAmount;

  @Column(name = "seller_gross_amount", nullable = false, updatable = false)
  private long sellerGrossAmount;

  @Column(name = "fee_amount", nullable = false, updatable = false)
  private long feeAmount;

  @Column(name = "seller_net_amount", nullable = false, updatable = false)
  private long sellerNetAmount;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected OrderClosure() {}

  public static OrderClosure completed(
      long orderId, long buyerId, long amount, LocalDateTime occurredAt) {
    return create(orderId, buyerId, null, FullFundingPolicy.completed(amount), occurredAt);
  }

  public static OrderClosure refunded(
      long orderId,
      long sellerId,
      long cancellationRequestId,
      long amount,
      LocalDateTime occurredAt) {
    return create(
        orderId, sellerId, cancellationRequestId, FullFundingPolicy.cancelled(amount), occurredAt);
  }

  /** 검수 무응답 타이머 만료에 따른 시스템 구매 확정. 정상 확정과 같은 10% 수수료로 분배한다. */
  public static OrderClosure autoConfirmed(
      long orderId, long timerId, long amount, LocalDateTime occurredAt) {
    if (orderId < 1 || timerId < 1 || occurredAt == null)
      throw new IllegalArgumentException("Invalid auto confirmation closure");
    var distribution = FullFundingPolicy.completed(amount);
    var closure = new OrderClosure();
    closure.orderId = orderId;
    closure.reason = "AUTO_CONFIRMED";
    closure.actorType = "SYSTEM";
    closure.actorId = null;
    closure.sourceTimerId = timerId;
    closure.paidAmount = distribution.fundedAmount();
    closure.buyerRefundAmount = distribution.buyerRefund();
    closure.feeAmount = distribution.fee();
    closure.sellerNetAmount = distribution.sellerPayout();
    closure.sellerGrossAmount = distribution.fee() + distribution.sellerPayout();
    closure.createdAt = occurredAt;
    return closure;
  }

  private static OrderClosure create(
      long orderId,
      long actorId,
      Long cancellationRequestId,
      FullFundingPolicy.Distribution distribution,
      LocalDateTime occurredAt) {
    if (orderId < 1
        || actorId < 1
        || occurredAt == null
        || (cancellationRequestId != null && cancellationRequestId < 1)) {
      throw new IllegalArgumentException("Invalid order closure");
    }
    var closure = new OrderClosure();
    closure.orderId = orderId;
    closure.reason = cancellationRequestId == null ? "COMPLETED" : "MUTUAL_CANCELLATION";
    closure.actorType = "MEMBER";
    closure.actorId = actorId;
    closure.sourceCancellationRequestId = cancellationRequestId;
    closure.paidAmount = distribution.fundedAmount();
    closure.buyerRefundAmount = distribution.buyerRefund();
    closure.feeAmount = distribution.fee();
    closure.sellerNetAmount = distribution.sellerPayout();
    closure.sellerGrossAmount = distribution.fee() + distribution.sellerPayout();
    closure.createdAt = occurredAt;
    return closure;
  }
}
