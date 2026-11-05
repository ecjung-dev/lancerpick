package com.example.lancerpick.chat.controller;

import com.example.lancerpick.chat.dto.ChatMessageResponse;
import com.example.lancerpick.chat.dto.ChatMessagesResponse;
import com.example.lancerpick.chat.dto.SendChatMessageRequest;
import com.example.lancerpick.chat.service.OrderChatService;
import com.example.lancerpick.common.security.CurrentMemberId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!demo")
@RequestMapping("/api/orders/{orderId}/chat/messages")
@RequiredArgsConstructor
public class OrderChatController {
  private final OrderChatService chatService;
  private final CurrentMemberId currentMemberId;

  @GetMapping
  public ResponseEntity<ChatMessagesResponse> list(
      Authentication authentication,
      @PathVariable @Min(1) long orderId,
      @RequestParam(required = false) Long beforeSequence,
      @RequestParam(required = false) Long afterSequence,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            chatService.list(
                currentMemberId.require(authentication),
                orderId,
                beforeSequence,
                afterSequence,
                limit));
  }

  @PostMapping
  public ResponseEntity<ChatMessageResponse> send(
      Authentication authentication,
      @PathVariable @Min(1) long orderId,
      @Valid @RequestBody SendChatMessageRequest request) {
    var result = chatService.send(currentMemberId.require(authentication), orderId, request);
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .cacheControl(CacheControl.noStore())
        .body(result.message());
  }
}
