package com.example.lancerpick.review.dto;

import java.time.OffsetDateTime;

public record ReviewResponse(String id, String orderId, String authorDisplayName,
        int rating, String content, OffsetDateTime createdAt) {}
