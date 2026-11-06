package com.example.lancerpick.chat.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;

class ChatSocketErrorHandlerTests {
  private final ChatSocketErrorHandler handler = new ChatSocketErrorHandler();

  @Test
  void errorFrameCarriesFixedCodeAndServerTraceIdOnly() {
    var frame =
        handler.handleClientMessageProcessingError(
            null, new RuntimeException(new AccessDeniedException("CHAT_RATE_LIMITED")));

    String body = new String(frame.getPayload(), StandardCharsets.UTF_8);
    assertThat(body).matches("\\{\"code\":\"CHAT_RATE_LIMITED\",\"traceId\":\"[0-9a-f-]{36}\"}");
    assertThat(StompHeaderAccessor.wrap(frame).getMessage()).isEqualTo("CHAT_RATE_LIMITED");
  }

  @Test
  void unknownFailureMessageIsNotEchoed() {
    var frame =
        handler.handleClientMessageProcessingError(null, new IllegalStateException("secret-token"));

    String body = new String(frame.getPayload(), StandardCharsets.UTF_8);
    assertThat(body).startsWith("{\"code\":\"FORBIDDEN\"").doesNotContain("secret-token");
  }
}
