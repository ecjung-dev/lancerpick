package com.example.lancerpick.chat.dto;

import jakarta.validation.constraints.NotNull;

public record SendChatMessageRequest(@NotNull String clientMessageId, @NotNull String content) {
  @Override
  public String toString() {
    return "SendChatMessageRequest[content=***]";
  }
}
