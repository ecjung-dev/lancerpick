package com.example.lancerpick.chat.realtime;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;

/** 승인 목록 외 프레임·목적지는 기본 거부한다. 메시지의 회원/주문 권한은 저장 서비스가 다시 검사한다. */
@RequiredArgsConstructor
public class ChatInboundGuard implements ChannelInterceptor {
  private final ChatSocketSessions sessions;
  private static final Set<String> SUBSCRIPTIONS =
      Set.of("/user/queue/chat", "/user/queue/chat-errors");

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    var headers = StompHeaderAccessor.wrap(message);
    String socketId = headers.getSessionId();
    if (headers.getCommand() == StompCommand.DISCONNECT) return message;
    var identity = sessions.requireValid(socketId);
    if (headers.getMessageType() == SimpMessageType.CONNECT) {
      if (!identity
          .memberId()
          .toString()
          .equals(headers.getFirstNativeHeader("X-Chat-Member-Id"))) {
        throw new AccessDeniedException("AUTHENTICATION_REQUIRED");
      }
      sessions.connected(socketId);
      return message;
    }
    sessions.requireConnected(socketId);
    if (headers.getMessageType() == SimpMessageType.HEARTBEAT) return message;
    if (headers.getCommand() == StompCommand.SUBSCRIBE
        && SUBSCRIPTIONS.contains(headers.getDestination())) {
      sessions.subscribe(socketId, headers.getSubscriptionId(), headers.getDestination());
      return message;
    }
    if (headers.getCommand() == StompCommand.UNSUBSCRIBE) {
      sessions.unsubscribe(socketId, headers.getSubscriptionId());
      return message;
    }
    if (headers.getCommand() == StompCommand.SEND
        && headers.getDestination() != null
        && headers.getDestination().matches("/app/orders/[1-9][0-9]{0,18}/chat/messages")) {
      try {
        Long.parseLong(headers.getDestination().split("/")[3]);
      } catch (NumberFormatException invalid) {
        throw new AccessDeniedException("FORBIDDEN");
      }
      sessions.requireSendCapacity(socketId);
      return message;
    }
    throw new AccessDeniedException("FORBIDDEN");
  }
}
