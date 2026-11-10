package com.example.lancerpick.settlement.dto;

public record SettlementResponse(
    String id,
    String orderId,
    long grossAmount,
    int feeRateBps,
    long feeAmount,
    long sellerAmount,
    String createdAt,
    String productTitle,
    String buyerId,
    String buyerDisplayName,
    String policyVersion,
    String payoutStatus) {}
