package com.example.lancerpick.product.dto;

import com.example.lancerpick.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public record ProductSearchOptions(Integer minPrice, Integer maxPrice, Integer maxDurationDays, Sort sort, Long sellerId) {
    public enum Sort { NEWEST, PRICE_ASC, PRICE_DESC }

    public static final ProductSearchOptions DEFAULT = new ProductSearchOptions(null, null, null, Sort.NEWEST, null);

    public static ProductSearchOptions parse(
            String minPrice, String maxPrice, String maxDurationDays, String sort, String sellerId) {
        Integer min = parseBound(minPrice, 1_000_000);
        Integer max = parseBound(maxPrice, 1_000_000);
        Integer days = parseBound(maxDurationDays, 30);
        if (min != null && max != null && min > max) throw invalid();
        Sort parsedSort;
        try {
            parsedSort = sort == null ? Sort.NEWEST : Sort.valueOf(sort);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
        return new ProductSearchOptions(min, max, days, parsedSort, parseSellerId(sellerId));
    }

    private static Long parseSellerId(String value) {
        if (value == null) return null;
        if (value.matches("[0-9]{1,19}")) {
            try {
                long id = Long.parseLong(value);
                if (id > 0) return id;
            } catch (NumberFormatException ignored) {
                // BIGINT 범위를 벗어난 입력도 같은 계약으로 거절한다.
            }
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "판매자 번호는 양의 정수 범위여야 합니다.");
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
