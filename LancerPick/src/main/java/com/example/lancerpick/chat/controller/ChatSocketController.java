package com.example.lancerpick.chat.controller;

import com.example.lancerpick.chat.dto.ChatSendError;
import com.example.lancerpick.chat.dto.SendChatMessageRequest;
import com.example.lancerpick.chat.realtime.ChatSocketSessions;
import com.example.lancerpick.chat.service.ChatRealtimeDelivery;
import com.example.lancerpick.chat.service.OrderChatService;
import com.example.lancerpick.common.exception.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/**
 * 소켓 전송 한 건마다 traceId를 새로 만들어 로그(MDC)와 오류 프레임에 함께 싣는다. HTTP 요청의 TraceIdFilter와 같은
 * 규칙이며, 클라이언트가 보낸 식별자는 사용하지 않는다.
 */
@Slf4j
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
    String traceId = UUID.randomUUID().toString();
    log.warn("Chat frame malformed traceId={}", traceId);
    delivery.error(
        socketId,
        new ChatSendError(
            null,
            null,
            HttpStatus.BAD_REQUEST.value(),
            "INVALID_REQUEST",
            "메시지 형식을 확인해 주세요.",
            traceId));
  }

  @MessageMapping("/orders/{orderId}/chat/messages")
  public void send(
      @DestinationVariable String orderId,
      SendChatMessageRequest request,
      @Header("simpSessionId") String socketId) {
    var identity = sessions.requireValid(socketId);
    String traceId = UUID.randomUUID().toString();
    String clientMessageId = request == null ? null : request.clientMessageId();
    try (var ignored = MDC.putCloseable("traceId", traceId)) {
      try {
        var result = chatService.send(identity.memberId(), Long.parseLong(orderId), request);
        if (!result.created()) delivery.acknowledge(socketId, result.message());
      } catch (BusinessException failure) {
        delivery.error(
            socketId,
            new ChatSendError(
                orderId,
                clientMessageId,
                failure.status().value(),
                failure.code(),
                failure.getMessage(),
                traceId));
      } catch (RuntimeException failure) {
        log.error(
            "Chat send failed traceId={} type={}", traceId, failure.getClass().getName());
        delivery.error(
            socketId,
            new ChatSendError(
                orderId,
                clientMessageId,
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "CHAT_SEND_FAILED",
                "저장 결과를 다시 확인해 주세요.",
                traceId));
      }
    }
  }
}
