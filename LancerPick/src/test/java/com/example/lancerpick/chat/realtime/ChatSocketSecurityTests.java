package com.example.lancerpick.chat.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.lancerpick.common.security.AuthVersionLookup;
import com.example.lancerpick.common.security.LoginMember;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.socket.WebSocketSession;

class ChatSocketSecurityTests {
  AuthVersionLookup versions = mock(AuthVersionLookup.class);
  HttpSession http = mock(HttpSession.class);
  WebSocketSession socket = mock(WebSocketSession.class);
  ChatSocketSessions sessions;
  ChatInboundGuard guard;
  static final long NOW = Instant.parse("2026-11-05T00:00:00Z").toEpochMilli();

  @BeforeEach
  void fixture() throws Exception {
    sessions =
        new ChatSocketSessions(versions, Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));
    guard = new ChatInboundGuard(sessions);
    var identity = new ChatSocketIdentity(7L, 0L, "opaque-route");
    when(socket.getId()).thenReturn("socket");
    when(socket.isOpen()).thenReturn(true);
    when(socket.getPrincipal())
        .thenReturn(UsernamePasswordAuthenticationToken.authenticated(identity, null, List.of()));
    when(socket.getAttributes())
        .thenReturn(
            Map.of(
                ChatSocketSessions.HTTP_SESSION,
                http,
                ChatSocketSessions.HTTP_SESSION_ID,
                "http-session"));
    when(http.getId()).thenReturn("http-session");
    when(http.getMaxInactiveInterval()).thenReturn(1800);
    when(http.getLastAccessedTime()).thenReturn(NOW);
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            new LoginMember(7L, "USER", 0L), null, List.of()));
    when(http.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
        .thenReturn(context);
    when(versions.currentAuthVersion(7L)).thenReturn(Optional.of(0L));
    sessions.register(socket);
  }

  @Test
  void expiredHttpSessionCannotBeKeptAliveBySocketFrames() throws Exception {
    when(http.getLastAccessedTime()).thenReturn(NOW - 1800000);
    assertThatThrownBy(() -> sessions.requireValid("socket"))
        .isInstanceOf(AccessDeniedException.class);
    verify(socket).close(any());
    assertThat(sessions.allowOutbound("socket")).isFalse();
  }

  @Test
  void rotatedSessionAndAuthVersionInvalidateEvenBeforeScheduledSweep() throws Exception {
    when(http.getId()).thenReturn("rotated-session");
    assertThat(sessions.allowOutbound("socket")).isFalse();
    verify(socket).close(any());
  }

  @Test
  void changedPasswordVersionClosesIdleSocketOnSweep() throws Exception {
    when(versions.currentAuthVersion(7L)).thenReturn(Optional.of(1L));
    sessions.reapExpired();
    verify(socket).close(any());
    assertThat(sessions.allowOutbound("socket")).isFalse();
  }

  @Test
  void dbUnavailableFailsClosedWithoutExposingContent() {
    when(versions.currentAuthVersion(7L))
        .thenThrow(new IllegalStateException("database unavailable"));
    assertThat(sessions.allowOutbound("socket")).isFalse();
    assertThatThrownBy(() -> sessions.requireValid("socket")).hasMessage("AUTHENTICATION_REQUIRED");
  }

  @Test
  void onlyTwoPrivateSubscriptionsAndValidApplicationSendAreAllowed() {
    connect();
    assertThat(guard.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/chat", "messages"), null))
        .isNotNull();
    assertThat(
            guard.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/chat-errors", "errors"), null))
        .isNotNull();
    assertThatThrownBy(
            () -> guard.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/chat", "third"), null))
        .hasMessage("FORBIDDEN");
    assertThatThrownBy(() ->
            guard.preSend(
                frame(StompCommand.SUBSCRIBE, "/user/opaque-route/queue/chat", "third"), null))
        .hasMessage("FORBIDDEN");
    assertThatThrownBy(() -> guard.preSend(frame(StompCommand.SEND, "/queue/chat", null), null))
        .hasMessage("FORBIDDEN");
    assertThatThrownBy(() ->
            guard.preSend(
                frame(StompCommand.SEND, "/app/orders/9223372036854775808/chat/messages", null),
                null))
        .hasMessage("FORBIDDEN");
    assertThat(guard.preSend(frame(StompCommand.SEND, "/app/orders/11/chat/messages", null), null))
        .isNotNull();
  }

  @Test
  void sendFramesAreBoundedPerConnectionAndReconnectIsNotRequiredForValidTraffic() {
    connect();
    for (int index = 0; index < 20; index++)
      guard.preSend(frame(StompCommand.SEND, "/app/orders/11/chat/messages", null), null);
    assertThatThrownBy(() ->
            guard.preSend(frame(StompCommand.SEND, "/app/orders/11/chat/messages", null), null))
        .hasMessage("CHAT_RATE_LIMITED");
  }

  @Test
  void memberConnectionLimitClosesOnlyAdditionalSocket() throws Exception {
    var principal = socket.getPrincipal();
    var attributes = socket.getAttributes();
    for (int index = 1; index < 9; index++) {
      var additional = mock(WebSocketSession.class);
      when(additional.getId()).thenReturn("socket-" + index);
      when(additional.isOpen()).thenReturn(true);
      when(additional.getPrincipal()).thenReturn(principal);
      when(additional.getAttributes()).thenReturn(attributes);
      sessions.register(additional);
      if (index == 8)
        verify(additional)
            .close(new org.springframework.web.socket.CloseStatus(4429, "CONNECTION_LIMIT"));
    }
    assertThat(sessions.allowOutbound("socket")).isTrue();
    assertThat(sessions.allowOutbound("socket-8")).isFalse();
  }

  @Test
  void rawFramesAreBoundedBeforeEnteringOrderedQueue() throws Exception {
    for (int index = 0; index < 60; index++) assertThat(sessions.allowFrame("socket")).isTrue();
    assertThat(sessions.allowFrame("socket")).isFalse();
    verify(socket).close(new org.springframework.web.socket.CloseStatus(4429, "CHAT_RATE_LIMITED"));
  }

  @Test
  void sendBeforeConnectAndRepeatedConnectAreRejected() {
    assertThatThrownBy(() ->
            guard.preSend(frame(StompCommand.SEND, "/app/orders/11/chat/messages", null), null))
        .hasMessage("FORBIDDEN");
    connect();
    assertThatThrownBy(this::connect).hasMessage("FORBIDDEN");
  }

  void connect() {
    guard.preSend(frame(StompCommand.CONNECT, null, null), null);
  }

  Message<byte[]> frame(StompCommand command, String destination, String subscriptionId) {
    var headers = StompHeaderAccessor.create(command);
    headers.setSessionId("socket");
    headers.setNativeHeader("X-Chat-Member-Id", "7");
    if (destination != null) headers.setDestination(destination);
    if (subscriptionId != null) headers.setSubscriptionId(subscriptionId);
    return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
  }
}
