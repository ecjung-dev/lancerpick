package com.example.lancerpick.order.dto;

public record CancellationResultResponse(OrderDetailResponse.CancellationRequest cancellationRequest, OrderDetailResponse order) {}
