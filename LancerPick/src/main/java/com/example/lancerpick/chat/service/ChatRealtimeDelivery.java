package com.example.lancerpick.chat.service;

import com.example.lancerpick.chat.dto.ChatMessageResponse;
import com.example.lancerpick.chat.dto.ChatSendError;
import com.example.lancerpick.chat.event.ChatMessageStored;
import com.example.lancerpick.chat.realtime.ChatSocketIdentity;
import com.example.lancerpick.chat.realtime.ChatSocketSessions;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Profile("!demo")
@RequiredArgsConstructor
public class ChatRealtimeDelivery {
  private final ChatSocketSessions sessions;
  private final ObjectProvider<SimpMessagingTemplate> messagingTemplate;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void committed(ChatMessageStored event) {
    sessions.forMembers(
        Set.of(event.buyerId(), event.sellerId()),
        identity -> deliver(identity, "/queue/chat", event.message()));
  }

  public void acknowledge(String socketId, ChatMessageResponse message) {
    deliverToSession(socketId, "/queue/chat", message);
  }

  public void error(String socketId, ChatSendError error) {
    deliverToSession(socketId, "/queue/chat-errors", error);
  }

  private void deliverToSession(String socketId, String destination, Object payload) {
    try {
      deliver(sessions.requireValid(socketId), destination, payload);
    } catch (AccessDeniedException expired) {
      /* 업무 처리 직후 세션이 폐기된 경우에도 전달을 중단하고 프레임 원문을 기록하지 않는다. */
    }
  }

  private void deliver(ChatSocketIdentity identity, String destination, Object payload) {
    try {
      var template = messagingTemplate.getIfAvailable();
      if (template != null) template.convertAndSendToUser(identity.getName(), destination, payload);
    } catch (RuntimeException unavailable) {
      /* 커밋된 저장은 취소하지 않는다. 클라이언트가 순번 조회로 복구한다. */
    }
  }
}
