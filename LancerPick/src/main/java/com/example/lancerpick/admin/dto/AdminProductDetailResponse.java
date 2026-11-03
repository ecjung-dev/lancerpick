package com.example.lancerpick.admin.dto;

import com.example.lancerpick.admin.dto.AdminProductSummaryResponse.Category;
import com.example.lancerpick.admin.dto.AdminProductSummaryResponse.Seller;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/** API 3·9절: ProductDetail 필드 + 관리자 조회용 isHidden. */
public record AdminProductDetailResponse(
        String id,
        String title,
        long priceAmount,
        int durationDays,
        Category category,
        Seller seller,
        String description,
        String scopeDescription,
        String preparationDescription,
        long version,
        String saleStatus,
        @JsonProperty("isHidden") boolean isHidden,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
