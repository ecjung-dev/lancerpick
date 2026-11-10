package com.example.lancerpick.common.web;

import java.util.List;

/** 성공 응답을 별도 envelope로 감싸지 않고 목록 계약 자체를 반환한다. */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    /** 목록 API 공통 한 페이지 최대 개수. */
    public static final int MAX_SIZE = 100;

    public PageResponse {
        items = List.copyOf(items);
    }

    /** 목록 요청 범위(page 0 이상, size 1~{@value #MAX_SIZE}) 판정만 한다. 오류 코드·문구는 각 서비스가 정한다. */
    public static boolean isValidRequest(int page, int size) {
        return page >= 0 && size >= 1 && size <= MAX_SIZE;
    }

    /** 올림 나눗셈 전체 페이지 수. int 범위를 넘으면 ArithmeticException(Math.toIntExact)으로 실패한다. */
    public static int totalPages(long total, int size) {
        return Math.toIntExact(total / size + (total % size == 0 ? 0 : 1));
    }
}
