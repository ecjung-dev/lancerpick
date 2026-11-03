package com.example.lancerpick.product.dto;

public record ProductSummaryResponse(
        String id,
        String title,
        long priceAmount,
        int durationDays,
        CategoryResponse category,
        Seller seller
) {

    public record Seller(
            String id,
            String displayName
    ) {
    }
}