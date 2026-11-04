package com.example.lancerpick.review.dto;

import java.time.OffsetDateTime;

/** 공개 응답에는 주문 번호나 회원 식별 정보를 포함하지 않는다. */
public record PublicReviewResponse(String id, String authorDisplayName,
        int rating, String content, OffsetDateTime createdAt) {}
