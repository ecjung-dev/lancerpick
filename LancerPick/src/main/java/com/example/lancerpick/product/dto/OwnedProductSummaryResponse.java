package com.example.lancerpick.product.dto;

import java.time.OffsetDateTime;

/** 본인 목록 전용 요약. 공개 목록 DTO의 노출 범위를 변경하지 않는다. */
public record OwnedProductSummaryResponse(
        String id, String title, long priceAmount, int durationDays,
        CategoryResponse category, ProductSummaryResponse.Seller seller,
        String saleStatus, boolean isHidden, long version,
        OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
