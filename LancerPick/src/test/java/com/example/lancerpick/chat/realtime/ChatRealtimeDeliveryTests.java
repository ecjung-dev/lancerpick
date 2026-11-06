package com.example.lancerpick.chat.realtime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.lancerpick.chat.dto.ChatMessageResponse;
import com.example.lancerpick.chat.dto.ChatSendError;
import com.example.lancerpick.chat.service.ChatRealtimeDelivery;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

class ChatRealtimeDeliveryTests {
  @Test
  @SuppressWarnings("unchecked")
  void revokedSessionBetweenStorageAndResponseNeverPublishesOrLeaksIntoProtocolError() {
    var sessions = mock(ChatSocketSessions.class);
    ObjectProvider<SimpMessagingTemplate> template = mock(ObjectProvider.class);
    var delivery = new ChatRealtimeDelivery(sessions, template);
    when(sessions.requireValid("expired"))
        .thenThrow(new AccessDeniedException("AUTHENTICATION_REQUIRED"));
    String key = UUID.randomUUID().toString();
    var message =
        new ChatMessageResponse(
            "1", "11", "1", "7", key, "비공개 본문", OffsetDateTime.parse("2026-11-05T00:00:00Z"));
    assertThatCode(() -> delivery.acknowledge("expired", message)).doesNotThrowAnyException();
    assertThatCode(
            () -> delivery.error("expired", new ChatSendError("11", key, 409, "CHAT_CLOSED", "종료된 주문", "trace")))
        .doesNotThrowAnyException();
    verifyNoInteractions(template);
  }
}
