package com.example.lancerpick.chat.controller;

import com.example.lancerpick.chat.dto.ChatSendError;
import com.example.lancerpick.chat.dto.SendChatMessageRequest;
import com.example.lancerpick.chat.realtime.ChatSocketSessions;
import com.example.lancerpick.chat.service.ChatRealtimeDelivery;
import com.example.lancerpick.chat.service.OrderChatService;
import com.example.lancerpick.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@Profile("!demo")
@RequiredArgsConstructor
public class ChatSocketController {
  private final OrderChatService chatService;
  private final ChatSocketSessions sessions;
  private final ChatRealtimeDelivery delivery;

  @MessageExceptionHandler(Exception.class)
  public void malformed(@Header("simpSessionId") String socketId) {
    // JSON 변환 실패도 프레임 본문·토큰·예외 원문을 로그나 응답에 넣지 않는다.
    if (!sessions.allowOutbound(socketId)) return;
    delivery.error(socketId, new ChatSendError(null, null, "INVALID_REQUEST", "메시지 형식을 확인해 주세요."));
  }

  @MessageMapping("/orders/{orderId}/chat/messages")
  public void send(
      @DestinationVariable String orderId,
      SendChatMessageRequest request,
      @Header("simpSessionId") String socketId) {
    var identity = sessions.requireValid(socketId);
    try {
      var result = chatService.send(identity.memberId(), Long.parseLong(orderId), request);
      if (!result.created()) delivery.acknowledge(socketId, result.message());
    } catch (BusinessException failure) {
      delivery.error(
          socketId,
          new ChatSendError(
              orderId,
              request == null ? null : request.clientMessageId(),
              failure.code(),
              failure.getMessage()));
    } catch (RuntimeException failure) {
      delivery.error(
          socketId,
          new ChatSendError(
              orderId,
              request == null ? null : request.clientMessageId(),
              "CHAT_SEND_FAILED",
              "저장 결과를 다시 확인해 주세요."));
    }
  }
}
