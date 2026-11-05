package com.example.lancerpick.payment.entity;

import com.example.lancerpick.payment.service.FullFundingPolicy;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@lombok.Getter
@Entity
@Table(name = "order_funds")
public class OrderFunds {
  @Id
  @Column(name = "order_id")
  private Long orderId;

  @Column(name = "payment_id", nullable = false, updatable = false)
  private long paymentId;

  @Column(nullable = false, updatable = false)
  private long amount;

  @Column(name = "remaining_amount", nullable = false)
  private long remainingAmount;

  @Column(nullable = false, length = 16)
  private String status;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "held_at", nullable = false, updatable = false)
  private LocalDateTime heldAt;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "resolved_at")
  private LocalDateTime resolvedAt;

  protected OrderFunds() {}

  public static OrderFunds hold(long order, long payment, long amount, LocalDateTime at) {
    FullFundingPolicy.requireAmount(amount);
    if (order < 1 || payment < 1 || at == null)
      throw new IllegalArgumentException("Invalid funding metadata");
    var f = new OrderFunds();
    f.orderId = order;
    f.paymentId = payment;
    f.amount = amount;
    f.remainingAmount = amount;
    f.status = "HELD";
    f.heldAt = at;
    return f;
  }

  public void release(LocalDateTime at) {
    resolve("RELEASED", at);
  }

  public void refund(LocalDateTime at) {
    resolve("REFUNDED", at);
  }

  private void resolve(String target, LocalDateTime at) {
    if (!"HELD".equals(status) || at == null || at.isBefore(heldAt))
      throw new IllegalStateException("Funds already resolved or invalid clock");
    remainingAmount = 0;
    status = target;
    resolvedAt = at;
  }
}
