package com.example.lancerpick.seller.dto;

public record SellerProfileResponse(
        String id,
        String displayName,
        String introduction,
        String specialty,
        String portfolioUrl
) {
}