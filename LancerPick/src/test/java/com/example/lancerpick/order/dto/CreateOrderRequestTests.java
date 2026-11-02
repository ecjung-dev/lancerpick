package com.example.lancerpick.order.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.lancerpick.common.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateOrderRequestTests {

    @Test
    void 상품버전은_0을_허용한다() {
        var request = new CreateOrderRequest(
                "15",
                0L,
                "파란색으로 제작해 주세요.",
                "c00af24a-391c-48d8-9754-d5591ce7df22"
        );

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(request);

            assertThat(violations).isEmpty();
        }
    }

    @Test
    void 상품버전을_생략하면_거절한다() {
        var request = new CreateOrderRequest(
                "15",
                null,
                "파란색으로 제작해 주세요.",
                "c00af24a-391c-48d8-9754-d5591ce7df22"
        );

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(request);

            assertThat(violations)
                    .extracting(v -> v.getPropertyPath().toString())
                    .containsExactly("expectedProductVersion");
        }
    }

    @Test
    void 정상_상품ID를_Long으로_변환한다() {
        var request = new CreateOrderRequest(
                "15", 0L, "요구사항",
                "c00af24a-391c-48d8-9754-d5591ce7df22"
        );

        assertThat(request.productIdAsLong()).isEqualTo(15L);
    }

    @Test
    void 잘못된_상품ID를_거절한다() {
        String[] invalidIds = {
                null,
                "",
                " ",
                "abc",
                "0",
                "-1",
                "1.5",
                "9223372036854775808"
        };

        for (String id : invalidIds) {
            var request = new CreateOrderRequest(
                    id, 0L, "요구사항",
                    "c00af24a-391c-48d8-9754-d5591ce7df22"
            );

            assertThatThrownBy(request::productIdAsLong)
                    .as("상품 ID: %s", id)
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.code())
                                    .isEqualTo("VALIDATION_FAILED"));
        }
    }

    @Test
    void 요청키를_UUID로_변환하고_소문자로_표준화한다() {
        var request = new CreateOrderRequest(
                "15",
                0L,
                "요구사항",
                "C00AF24A-391C-48D8-9754-D5591CE7DF22"
        );

        var key = request.requestKeyAsUuid();

        assertThat(key.toString())
                .isEqualTo("c00af24a-391c-48d8-9754-d5591ce7df22");
    }

    @Test
    void 잘못된_요청키를_거절한다() {
        String[] invalidKeys = {
                null,
                "",
                " ",
                "not-a-uuid",
                "1-1-1-1-1",
                "c00af24a391c48d89754d5591ce7df22",
                "g00af24a-391c-48d8-9754-d5591ce7df22"
        };

        for (String key : invalidKeys) {
            var request = new CreateOrderRequest(
                    "15", 0L, "요구사항", key
            );

            assertThatThrownBy(request::requestKeyAsUuid)
                    .as("요청 키: %s", key)
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.code())
                                    .isEqualTo("VALIDATION_FAILED"));
        }
    }

    @Test
    void 요구사항의_앞뒤공백을_제거하고_줄바꿈을_통일한다() {
        var request = new CreateOrderRequest(
                "15",
                0L,
                "\u3000첫 줄\r\n다음 줄\u00A0",
                "c00af24a-391c-48d8-9754-d5591ce7df22"
        );

        assertThat(request.normalizedRequirements())
                .isEqualTo("첫 줄\n다음 줄");
    }

    @Test
    void 요구사항은_이모지_5000개까지_허용한다() {
        String requirements = "😀".repeat(5000);

        var request = new CreateOrderRequest(
                "15", 0L, requirements,
                "c00af24a-391c-48d8-9754-d5591ce7df22"
        );

        assertThat(request.normalizedRequirements())
                .isEqualTo(requirements);
    }

    @Test
    void 요구사항이_5000자를_초과하면_거절한다() {
        var request = new CreateOrderRequest(
                "15", 0L, "😀".repeat(5001),
                "c00af24a-391c-48d8-9754-d5591ce7df22"
        );

        assertThatThrownBy(request::normalizedRequirements)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void 요구사항이_없거나_공백뿐이면_거절한다() {
        String[] invalidRequirements = {
                null,
                "",
                " ",
                "\r\n",
                "\u00A0\u3000"
        };

        for (String requirements : invalidRequirements) {
            var request = new CreateOrderRequest(
                    "15", 0L, requirements,
                    "c00af24a-391c-48d8-9754-d5591ce7df22"
            );

            assertThatThrownBy(request::normalizedRequirements)
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.code())
                                    .isEqualTo("VALIDATION_FAILED"));
        }
    }
}