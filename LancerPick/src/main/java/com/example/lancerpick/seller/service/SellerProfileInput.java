package com.example.lancerpick.seller.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.text.TextNormalizer;
import com.example.lancerpick.seller.dto.SellerProfileRequest;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.http.HttpStatus;

public record SellerProfileInput(
        String introduction,
        String specialty,
        String portfolioUrl
) {

    public static SellerProfileInput from(SellerProfileRequest request) {
        if (request == null) {
            throw invalid("프로필 정보를 입력해 주세요.");
        }

        return new SellerProfileInput(
                requiredText(request.introduction(), 2000, "소개"),
                requiredText(request.specialty(), 100, "전문 분야"),
                optionalUrl(request.portfolioUrl())
        );
    }

    private static String requiredText(
            String raw, int maxLength, String label) {

        if (raw == null) {
            throw invalid(label + "를 입력해 주세요.");
        }

        String value = TextNormalizer.normalize(raw);
        int length = value.codePointCount(0, value.length());

        if (length < 1 || length > maxLength) {
            throw invalid(
                    label + "는 공백 정리 후 1~"
                            + maxLength + "자여야 합니다."
            );
        }

        return value;
    }

    private static String optionalUrl(String raw) {
        if (raw == null) {
            return null;
        }

        String value = TextNormalizer.normalize(raw);

        if (value.isEmpty()) {
            return null;
        }

        if (value.codePointCount(0, value.length()) > 2048) {
            throw invalid("포트폴리오 주소는 최대 2048자입니다.");
        }

        try {
            URI uri = new URI(value);

            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null) {

                throw invalidUrl();
            }

            return value;
        } catch (URISyntaxException exception) {
            throw invalidUrl();
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                message
        );
    }

    private static BusinessException invalidUrl() {
        return new BusinessException(
                HttpStatus.BAD_REQUEST,
                "INVALID_URL",
                "포트폴리오는 사용자 정보가 없는 HTTPS 주소여야 합니다."
        );
    }
}