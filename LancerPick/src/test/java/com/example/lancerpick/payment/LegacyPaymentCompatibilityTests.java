package com.example.lancerpick.payment;

import static org.junit.jupiter.api.Assertions.*;

import com.example.lancerpick.payment.entity.Payment;
import com.example.lancerpick.payment.entity.PaymentStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class LegacyPaymentCompatibilityTests {
  @Test
  void fullRefundKeepsLegacyMetadataAndCannotBeRepeated() {
    var paidAt = LocalDateTime.of(2026, 11, 5, 0, 0);
    var payment = Payment.create(1, 999, paidAt);
    assertEquals("LEGACY_V1", payment.getOrderPolicyVersion());
    assertEquals("FULL", payment.getInstallmentType());
    assertEquals(0, payment.getRefundedAmount());
    payment.refund(paidAt.plusMinutes(1));
    assertEquals(999, payment.getRefundedAmount());
    assertEquals(PaymentStatus.REFUNDED, payment.status());
    assertThrows(IllegalStateException.class, () -> payment.refund(paidAt.plusMinutes(2)));
    assertEquals(paidAt.plusMinutes(1), payment.getRefundedAt());
  }
}
