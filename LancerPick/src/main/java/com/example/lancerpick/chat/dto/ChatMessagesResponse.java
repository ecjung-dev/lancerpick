package com.example.lancerpick.chat.dto;

import java.util.List;

/** items는 과거/복구 조회 모두 순번 오름차순. nextCursor는 hasMore일 때만 제공한다. */
public record ChatMessagesResponse(
    List<ChatMessageResponse> items,
    String direction,
    String nextCursor,
    boolean hasMore,
    String lastSequence,
    boolean canSend) {
  public ChatMessagesResponse {
    items = List.copyOf(items);
  }
}
