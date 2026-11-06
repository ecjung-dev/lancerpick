package com.example.lancerpick.product.dto;

import java.time.OffsetDateTime;

/** 공개 상품 상세. 숨김 여부나 판매자의 개인 정보는 노출하지 않는다. */
public record ProductDetailResponse(
        String id, String title, long priceAmount, int durationDays,
        CategoryResponse category, ProductSummaryResponse.Seller seller,
        String description, String scopeDescription, String preparationDescription,
        long version, String saleStatus, OffsetDateTime createdAt, OffsetDateTime updatedAt,
        Long minimumOrderAmount) {
}
