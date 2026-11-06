package com.example.lancerpick.chat.dto;

import com.example.lancerpick.chat.entity.OrderChatMessage;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ChatMessageResponse(
    String id,
    String orderId,
    String sequence,
    String senderId,
    String clientMessageId,
    String content,
    OffsetDateTime createdAt) {
  public static ChatMessageResponse from(OrderChatMessage message) {
    return new ChatMessageResponse(
        message.getId().toString(),
        Long.toString(message.getOrderId()),
        Long.toString(message.getSequence()),
        Long.toString(message.getSenderId()),
        message.getClientMessageId(),
        message.getContent(),
        message.getCreatedAt().atOffset(ZoneOffset.UTC));
  }

  @Override
  public String toString() {
    return "ChatMessageResponse[id=" + id + ", sequence=" + sequence + ", content=***]";
  }
}
