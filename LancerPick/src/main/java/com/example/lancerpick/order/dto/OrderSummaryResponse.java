package com.example.lancerpick.order.dto;

import java.time.OffsetDateTime;

public record OrderSummaryResponse(String id, String status, String productTitle, long priceAmount,
        OrderDetailResponse.Participant counterparty, OffsetDateTime createdAt, OffsetDateTime currentDueAt) {}
