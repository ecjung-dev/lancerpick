package com.example.lancerpick.product.dto;

import java.time.OffsetDateTime;

public record OwnedProductResponse(String id, String title, long priceAmount, int durationDays,
        CategoryResponse category, ProductSummaryResponse.Seller seller, String description,
        String scopeDescription, String preparationDescription, long version, String saleStatus,
        boolean isHidden, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
