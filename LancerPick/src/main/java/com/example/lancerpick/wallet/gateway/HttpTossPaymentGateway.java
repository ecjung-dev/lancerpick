package com.example.lancerpick.wallet.gateway;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 주입된 RestClient는 고정 토스 HTTPS 주소·시크릿·타임아웃을 설정한다. */
public class HttpTossPaymentGateway implements TossPaymentGateway {
    private final RestClient client;
    public HttpTossPaymentGateway(RestClient client) { this.client = client; }

    @Override public void requireAvailable() {}

    @Override
    public Payment confirm(String paymentKey, String orderId, long amount, String idempotencyKey) {
        try {
            return requireBody(client.post().uri("/v1/payments/confirm")
                    .header("Idempotency-Key", idempotencyKey)
                    .body(new Confirmation(paymentKey, orderId, amount))
                    .retrieve().body(Payment.class));
        } catch (RestClientException exception) {
            throw new TossGatewayException();
        }
    }

    @Override
    public Payment lookup(String paymentKey) {
        try {
            // gsk는 orderId 조회를 지원하지 않으므로 저장된 paymentKey를 사용한다.
            return requireBody(client.get().uri("/v1/payments/{paymentKey}", paymentKey)
                    .retrieve().body(Payment.class));
        } catch (RestClientException exception) {
            throw new TossGatewayException();
        }
    }

    private static Payment requireBody(Payment payment) {
        if (payment == null) throw new TossGatewayException();
        return payment;
    }

    private record Confirmation(String paymentKey, String orderId, long amount) {}
}
