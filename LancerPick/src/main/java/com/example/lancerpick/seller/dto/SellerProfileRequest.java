package com.example.lancerpick.seller.dto;

public record SellerProfileRequest(
        String introduction,
        String specialty,
        String portfolioUrl
) {
}