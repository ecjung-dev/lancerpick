package com.example.lancerpick.wallet.gateway;

import com.example.lancerpick.common.exception.BusinessException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@Profile("!demo")
public class TossPaymentConfiguration {
    @Bean
    @ConditionalOnProperty(name = "toss.topup-enabled", havingValue = "true")
    TossPaymentGateway httpTossPaymentGateway(Environment environment) {
        String secret = environment.getRequiredProperty("toss.secret-key");
        if (!(secret.startsWith("test_gsk_") || secret.startsWith("test_sk_")) || secret.isBlank()) {
            throw new IllegalStateException("Top-ups require a Toss test secret key");
        }
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(65));
        var client = RestClient.builder().baseUrl("https://api.tosspayments.com")
                .requestFactory(factory)
                .defaultHeaders(headers -> {
                    headers.setBasicAuth(secret, "");
                    headers.setContentType(MediaType.APPLICATION_JSON);
                }).build();
        return new HttpTossPaymentGateway(client);
    }

    @Bean
    @ConditionalOnProperty(name = "toss.topup-enabled", havingValue = "false", matchIfMissing = true)
    TossPaymentGateway disabledTossPaymentGateway() {
        return new TossPaymentGateway() {
            @Override public void requireAvailable() { throw unavailable(); }
            @Override public Payment confirm(String key, String orderId, long amount, String requestKey) { throw unavailable(); }
            @Override public Payment lookup(String key) { throw unavailable(); }
            private BusinessException unavailable() {
                return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                        "PAYMENT_PROVIDER_UNAVAILABLE", "토스 테스트 충전이 활성화되지 않았습니다.");
            }
        };
    }
}
