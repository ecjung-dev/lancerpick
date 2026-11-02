package com.example.lancerpick.wallet.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpTossPaymentGatewayTests {
    @Test
    void confirmSendsStoredAmountAndIdempotencyAndIgnoresExtraProviderFields() {
        var builder = RestClient.builder().baseUrl("https://api.tosspayments.com")
                .defaultHeaders(headers -> headers.setBasicAuth("test_gsk_test", ""));
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Idempotency-Key", "stable-request-key"))
                .andExpect(header("Authorization", "Basic dGVzdF9nc2tfdGVzdDo="))
                .andExpect(content().json("{\"paymentKey\":\"test-payment\",\"orderId\":\"topup_123\",\"amount\":50000}"))
                .andRespond(withSuccess("""
                        {"paymentKey":"test-payment","orderId":"topup_123","totalAmount":50000,
                         "balanceAmount":50000,"currency":"KRW","status":"DONE","approvedAt":"2030-01-01T00:00:00Z",
                         "extraProviderField":"ignored"}
                        """, MediaType.APPLICATION_JSON));
        var payment = new HttpTossPaymentGateway(builder.build()).confirm("test-payment", "topup_123", 50000, "stable-request-key");
        assertThat(payment.status()).isEqualTo("DONE");
        assertThat(payment.totalAmount()).isEqualTo(50000);
        server.verify();
    }

    @Test
    void recoveryUsesPaymentKeyLookup() {
        var builder = RestClient.builder().baseUrl("https://api.tosspayments.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/test-payment"))
                .andExpect(method(HttpMethod.GET)).andRespond(withSuccess("""
                        {"paymentKey":"test-payment","orderId":"topup_123","totalAmount":50000,
                         "balanceAmount":50000,"currency":"KRW","status":"IN_PROGRESS","approvedAt":null}
                        """, MediaType.APPLICATION_JSON));
        assertThat(new HttpTossPaymentGateway(builder.build()).lookup("test-payment").status()).isEqualTo("IN_PROGRESS");
        server.verify();
    }

    @Test
    void providerFailureDoesNotExposeRawBody() {
        var builder = RestClient.builder().baseUrl("https://api.tosspayments.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withServerError().body("private-provider-response"));
        assertThatThrownBy(() -> new HttpTossPaymentGateway(builder.build()).lookup("test-payment"))
                .isInstanceOf(TossGatewayException.class).hasMessage("Payment provider result is unavailable").hasNoCause();
        server.verify();
    }

    @Test
    void lookupEncodesReservedCharactersAsOneIdentifier() {
        var builder = RestClient.builder().baseUrl("https://api.tosspayments.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/key%2Fwith%3Fx"))
                .andRespond(withSuccess("""
                        {"paymentKey":"key/with?x","orderId":"topup_123","totalAmount":50000,
                         "balanceAmount":50000,"currency":"KRW","status":"IN_PROGRESS","approvedAt":null}
                        """, MediaType.APPLICATION_JSON));
        new HttpTossPaymentGateway(builder.build()).lookup("key/with?x");
        server.verify();
    }

    @Test
    void configurationRejectsLiveKeyWithoutExposingIt() {
        assertThatThrownBy(() -> new TossPaymentConfiguration().httpTossPaymentGateway(
                new MockEnvironment().withProperty("toss.secret-key", "live_gsk_test")))
                .isInstanceOf(IllegalStateException.class).hasMessage("Top-ups require a Toss test secret key");
    }
}
