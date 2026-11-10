package com.example.lancerpick.order.dto;

import com.example.lancerpick.common.exception.BusinessException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import com.example.lancerpick.common.text.TextNormalizer;
import com.example.lancerpick.common.text.UuidText;

import java.util.UUID;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateOrderRequest(
        @NotBlank
        @JsonDeserialize(using = OrderRequestJson.StringValue.class)
        String productId,

        @NotNull
        @PositiveOrZero
        @JsonDeserialize(using = OrderRequestJson.VersionValue.class)
        Long expectedProductVersion,

        @NotBlank
        @JsonDeserialize(using = OrderRequestJson.StringValue.class)
        String requirements,

        @NotBlank
        @JsonDeserialize(using = OrderRequestJson.StringValue.class)
        String requestKey
) {
    public Long productIdAsLong() {
        if (productId == null || !productId.matches("[0-9]+")) {
            throw invalidProductId();
        }

        try {
            long id = Long.parseLong(productId);

            if (id <= 0) {
                throw invalidProductId();
            }

            return id;
        } catch (NumberFormatException e) {
            throw invalidProductId();
        }
    }

    private BusinessException invalidProductId() {
        return new BusinessException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "상품 ID는 양의 정수 범위여야 합니다."
        );
    }

    public UUID requestKeyAsUuid() {
        return UuidText.parse(requestKey).orElseThrow(() -> new BusinessException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "요청 키는 UUID 형식이어야 합니다."
        ));
    }

    public String normalizedRequirements() {
        if (requirements == null) {
            throw invalidRequirements();
        }

        String normalized = TextNormalizer.normalize(requirements);
        int length = normalized.codePointCount(0, normalized.length());

        if (length < 1 || length > 5000) {
            throw invalidRequirements();
        }

        return normalized;
    }

    private BusinessException invalidRequirements() {
        return new BusinessException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "요구사항은 공백 정리 후 1~5,000자여야 합니다."
        );
    }
}
