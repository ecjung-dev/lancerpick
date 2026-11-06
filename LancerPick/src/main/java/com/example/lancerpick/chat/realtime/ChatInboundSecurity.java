package com.example.lancerpick.chat.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.messaging.web.csrf.XorCsrfChannelInterceptor;
import org.springframework.security.web.csrf.CsrfException;

/** 순서 보존 채널이 예외를 삼켜도 거부된 연결이 살아남지 않도록 직접 종료하고 프레임을 버린다. */
@Slf4j
@RequiredArgsConstructor
public class ChatInboundSecurity implements ChannelInterceptor {
  private final ChatSocketSessions sessions;
  private final XorCsrfChannelInterceptor csrf = new XorCsrfChannelInterceptor();

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    try {
      csrf.preSend(message, channel);
      return new ChatInboundGuard(sessions).preSend(message, channel);
    } catch (CsrfException rejected) {
      reject(message, "CSRF_INVALID");
    } catch (AccessDeniedException rejected) {
      String code =
          switch (rejected.getMessage()) {
            case "AUTHENTICATION_REQUIRED" -> "AUTHENTICATION_REQUIRED";
            case "CHAT_RATE_LIMITED" -> "CHAT_RATE_LIMITED";
            default -> "FORBIDDEN";
          };
      reject(message, code);
    }
    return null;
  }

  private void reject(Message<?> message, String code) {
    log.warn("Chat connection rejected: {}", code);
    sessions.reject(SimpMessageHeaderAccessor.getSessionId(message.getHeaders()), code);
  }
}
