package com.example.lancerpick.chat.controller;

import com.example.lancerpick.chat.dto.ChatThreadResponse;
import com.example.lancerpick.chat.service.OrderChatThreadQueryService;
import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.common.web.PageResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!demo")
public class OrderChatThreadController {
  private final OrderChatThreadQueryService queries;
  private final CurrentMemberId members;

  public OrderChatThreadController(OrderChatThreadQueryService queries, CurrentMemberId members) {
    this.queries = queries;
    this.members = members;
  }

  @GetMapping("/api/me/order-chats")
  public ResponseEntity<PageResponse<ChatThreadResponse>> list(
      Authentication authentication,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(queries.list(members.require(authentication), q, page, size));
  }
}
