package com.example.lancerpick.product.dto;

import com.example.lancerpick.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public record ProductSearchOptions(Integer minPrice, Integer maxPrice, Integer maxDurationDays, Sort sort) {
    public enum Sort { NEWEST, PRICE_ASC, PRICE_DESC }

    public static final ProductSearchOptions DEFAULT = new ProductSearchOptions(null, null, null, Sort.NEWEST);

    public static ProductSearchOptions parse(String minPrice, String maxPrice, String maxDurationDays, String sort) {
        Integer min = parseBound(minPrice, 1_000_000);
        Integer max = parseBound(maxPrice, 1_000_000);
        Integer days = parseBound(maxDurationDays, 30);
        if (min != null && max != null && min > max) throw invalid();
        try {
            return new ProductSearchOptions(min, max, days, sort == null ? Sort.NEWEST : Sort.valueOf(sort));
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static Integer parseBound(String value, int limit) {
        if (value == null) return null;
        if (!value.matches("[0-9]{1,7}")) throw invalid();
        int number = Integer.parseInt(value);
        if (number < 1 || number > limit) throw invalid();
        return number;
    }

    private static BusinessException invalid() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "가격 범위, 최대 작업 기간 또는 정렬 조건을 확인해 주세요.");
    }
}
