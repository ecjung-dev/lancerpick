package com.example.lancerpick.chat.dto;

import java.time.OffsetDateTime;

public record ChatThreadResponse(
    String orderId,
    String productTitle,
    Counterparty counterparty,
    String side,
    String orderStatus,
    boolean canSend,
    String lastMessagePreview,
    String lastSenderId,
    OffsetDateTime lastMessageAt,
    OffsetDateTime activityAt) {
  public record Counterparty(String id, String displayName) {}
}
