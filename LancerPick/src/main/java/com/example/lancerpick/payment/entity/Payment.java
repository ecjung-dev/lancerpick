package com.example.lancerpick.payment.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@lombok.Getter
@Entity
@Table(name = "payments")
public class Payment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false)
  private long orderId;

  @Column(name = "amount", nullable = false)
  private long amount;

  @Column(name = "order_policy_version", nullable = false, updatable = false, length = 32)
  private String orderPolicyVersion =
      com.example.lancerpick.common.policy.TradePolicyVersion.LEGACY_V1.name();

  @Column(name = "installment_type", nullable = false, updatable = false, length = 16)
  private String installmentType = "FULL";

  @Column(name = "refunded_amount", nullable = false)
  private long refundedAmount;

  @Column(name = "fee_rate_bps", nullable = false)
  private int feeRateBps;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "status", nullable = false, length = 10)
  private PaymentStatus status;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "paid_at", nullable = false)
  private LocalDateTime paidAt;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "refunded_at", nullable = true)
  private LocalDateTime refundedAt;

  protected Payment() {}

  public static Payment create(long orderId, long amount, LocalDateTime at) {
    if (orderId <= 0 || amount < 1 || amount > 1_000_000 || at == null)
      throw new IllegalArgumentException("Invalid payment");
    var p = new Payment();
    p.orderId = orderId;
    p.amount = amount;
    p.feeRateBps = 1000;
    p.status = PaymentStatus.PAID;
    p.paidAt = at;
    return p;
  }

  public void refund(LocalDateTime at) {
    if (status != PaymentStatus.PAID || at == null || at.isBefore(paidAt))
      throw new IllegalStateException("Invalid refund");
    status = PaymentStatus.REFUNDED;
    refundedAt = at;
    refundedAmount = amount;
  }

  public long id() {
    return id;
  }

  public long amount() {
    return amount;
  }

  public PaymentStatus status() {
    return status;
  }

  public int rate() {
    return feeRateBps;
  }

  public static Payment fullFunding(long order, long amount, LocalDateTime at) {
    com.example.lancerpick.payment.service.FullFundingPolicy.requireAmount(amount);
    var p = create(order, amount, at);
    p.orderPolicyVersion = "CONTRACT_V3";
    return p;
  }

  public long getOrderId() {
    return orderId;
  }
}
