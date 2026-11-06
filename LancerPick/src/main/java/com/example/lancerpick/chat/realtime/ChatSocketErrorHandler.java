package com.example.lancerpick.chat.realtime;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/** 프레임 오류에는 본문·스택·토큰을 싣지 않는다. 인증/구독 오류 후 연결은 종료된다. */
@Slf4j
public class ChatSocketErrorHandler extends StompSubProtocolErrorHandler {
  @Override
  public Message<byte[]> handleClientMessageProcessingError(
      Message<byte[]> request, Throwable failure) {
    String code = "FORBIDDEN";
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof CsrfException) {
        code = "CSRF_INVALID";
        break;
      }
      if ("AUTHENTICATION_REQUIRED".equals(cause.getMessage())
          || "CHAT_RATE_LIMITED".equals(cause.getMessage())) code = cause.getMessage();
    }
    // code는 위 고정 목록, traceId는 서버가 만든 UUID라 JSON 이스케이프가 필요 없다.
    String traceId = UUID.randomUUID().toString();
    var headers = StompHeaderAccessor.create(StompCommand.ERROR);
    headers.setMessage(code);
    headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
    headers.setLeaveMutable(true);
    log.warn("Chat frame rejected: {} traceId={}", code, traceId);
    return MessageBuilder.createMessage(
        ("{\"code\":\"" + code + "\",\"traceId\":\"" + traceId + "\"}")
            .getBytes(StandardCharsets.UTF_8),
        headers.getMessageHeaders());
  }
}
