package com.example.lancerpick.settlement.dto;

public record SettlementSummaryResponse(
    long completedCount, long grossAmount, long feeAmount, long sellerAmount) {}
