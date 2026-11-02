package com.example.lancerpick.wallet.gateway;

import java.time.OffsetDateTime;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public interface TossPaymentGateway {
    void requireAvailable();
    Payment confirm(String paymentKey, String orderId, long amount, String idempotencyKey);
    Payment lookup(String paymentKey);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payment(String paymentKey, String orderId, long totalAmount, long balanceAmount,
            String currency, String status, OffsetDateTime approvedAt) {}
}
