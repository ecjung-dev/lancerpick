package com.example.lancerpick.review.dto;

import java.util.List;

public record ProductReviewsResponse(
        List<PublicReviewResponse> items, int page, int size, long totalElements, int totalPages,
        Summary summary) {

    public record Summary(Double averageRating, long reviewCount) {}
}
