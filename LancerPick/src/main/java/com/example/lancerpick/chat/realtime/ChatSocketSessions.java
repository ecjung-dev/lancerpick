package com.example.lancerpick.chat.realtime;

import com.example.lancerpick.common.security.AuthVersionLookup;
import com.example.lancerpick.common.security.LoginMember;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/** 연결·발신·수신마다 HTTP 세션과 현재 인증 버전을 확인한다. 세션을 연장하지 않는다. */
@Component
@Profile("!demo")
@RequiredArgsConstructor
public class ChatSocketSessions {
  static final String HTTP_SESSION = ChatSocketSessions.class.getName() + ".httpSession";
  static final String HTTP_SESSION_ID = ChatSocketSessions.class.getName() + ".httpSessionId";
  private final AuthVersionLookup authVersionLookup;
  private final Clock clock;
  private final Map<String, Connection> connections = new ConcurrentHashMap<>();

  static final class Connection {
    final WebSocketSession socket;
    final HttpSession session;
    final String sessionId;
    final ChatSocketIdentity identity;
    final Map<String, String> subscriptions = new ConcurrentHashMap<>();
    final ArrayDeque<Long> sends = new ArrayDeque<>();
    final ArrayDeque<Long> frames = new ArrayDeque<>();
    volatile boolean connected;

    Connection(
        WebSocketSession socket,
        HttpSession session,
        String sessionId,
        ChatSocketIdentity identity) {
      this.socket = socket;
      this.session = session;
      this.sessionId = sessionId;
      this.identity = identity;
    }
  }

  public synchronized void register(WebSocketSession socket) throws IOException {
    if (!(socket.getPrincipal() instanceof Authentication authentication)
        || !(authentication.getPrincipal() instanceof ChatSocketIdentity identity)
        || !(socket.getAttributes().get(HTTP_SESSION) instanceof HttpSession session)
        || !(socket.getAttributes().get(HTTP_SESSION_ID) instanceof String sessionId)) {
      socket.close(new CloseStatus(4401, "AUTHENTICATION_REQUIRED"));
      return;
    }
    long memberConnections =
        connections.values().stream()
            .filter(value -> value.identity.memberId().equals(identity.memberId()))
            .count();
    if (memberConnections >= 8 || connections.size() >= 256) {
      socket.close(new CloseStatus(4429, "CONNECTION_LIMIT"));
      return;
    }
    var connection = new Connection(socket, session, sessionId, identity);
    connections.put(socket.getId(), connection);
    if (!valid(connection)) close(socket.getId());
  }

  public ChatSocketIdentity requireValid(String socketId) {
    var connection = socketId == null ? null : connections.get(socketId);
    if (connection == null || !valid(connection)) {
      close(socketId);
      throw new AccessDeniedException("AUTHENTICATION_REQUIRED");
    }
    return connection.identity;
  }

  public boolean allowOutbound(String socketId) {
    var connection = socketId == null ? null : connections.get(socketId);
    if (connection != null && valid(connection)) return true;
    if (socketId != null) close(socketId);
    return false;
  }

  public void connected(String socketId) {
    var connection = connections.get(socketId);
    if (connection == null) throw denied();
    synchronized (connection) {
      if (connection.connected) throw denied();
      connection.connected = true;
    }
  }

  public void requireConnected(String socketId) {
    var connection = connections.get(socketId);
    if (connection == null || !connection.connected) throw denied();
  }

  public void subscribe(String socketId, String subscriptionId, String destination) {
    var connection = connections.get(socketId);
    if (connection == null || subscriptionId == null || subscriptionId.length() > 100)
      throw denied();
    synchronized (connection) {
      if (connection.subscriptions.size() >= 2
          || connection.subscriptions.containsKey(subscriptionId)
          || connection.subscriptions.containsValue(destination)) throw denied();
      connection.subscriptions.put(subscriptionId, destination);
    }
  }

  public void unsubscribe(String socketId, String subscriptionId) {
    var connection = connections.get(socketId);
    if (connection != null && subscriptionId != null)
      connection.subscriptions.remove(subscriptionId);
  }

  public void requireSendCapacity(String socketId) {
    var connection = connections.get(socketId);
    if (connection == null) throw denied();
    synchronized (connection) {
      long now = clock.millis();
      while (!connection.sends.isEmpty() && connection.sends.getFirst() <= now - 10000)
        connection.sends.removeFirst();
      if (connection.sends.size() >= 20) throw new AccessDeniedException("CHAT_RATE_LIMITED");
      connection.sends.addLast(now);
    }
  }

  /** 순서 보존 큐에 넣기 전에 원시 프레임 유입도 제한한다. 하트비트·구독을 포함한다. */
  public boolean allowFrame(String socketId) {
    var connection = connections.get(socketId);
    if (connection == null) return false;
    synchronized (connection) {
      long now = clock.millis();
      while (!connection.frames.isEmpty() && connection.frames.getFirst() <= now - 10000)
        connection.frames.removeFirst();
      if (connection.frames.size() < 60) {
        connection.frames.addLast(now);
        return true;
      }
    }
    reject(socketId, "CHAT_RATE_LIMITED");
    return false;
  }

  public void forMembers(Set<Long> memberIds, Consumer<ChatSocketIdentity> action) {
    for (var connection : List.copyOf(connections.values())) {
      if (memberIds.contains(connection.identity.memberId())
          && allowOutbound(connection.socket.getId())) action.accept(connection.identity);
    }
  }

  public void remove(String socketId) {
    connections.remove(socketId);
  }

  public void close(String socketId) {
    reject(socketId, "AUTHENTICATION_REQUIRED");
  }

  public void reject(String socketId, String code) {
    if (socketId == null) return;
    var connection = connections.remove(socketId);
    if (connection == null) return;
    try {
      int status =
          switch (code) {
            case "AUTHENTICATION_REQUIRED" -> 4401;
            case "CHAT_RATE_LIMITED" -> 4429;
            default -> 4403;
          };
      connection.socket.close(new CloseStatus(status, code));
    } catch (IOException ignored) {
      /* 이미 종료된 소켓은 등록에서 제거한 것으로 끝낸다. */
    }
  }

  @EventListener
  public void sessionDestroyed(HttpSessionDestroyedEvent event) {
    for (var connection : List.copyOf(connections.values()))
      if (connection.sessionId.equals(event.getId())) close(connection.socket.getId());
  }

  @Scheduled(fixedDelayString = "${lancerpick.chat.session-check-ms:5000}")
  public void reapExpired() {
    for (var connection : List.copyOf(connections.values()))
      if (!valid(connection)) close(connection.socket.getId());
  }

  @PreDestroy
  public void shutdown() {
    for (String socketId : List.copyOf(connections.keySet())) close(socketId);
  }

  private boolean valid(Connection connection) {
    try {
      if (!connection.socket.isOpen() || !connection.sessionId.equals(connection.session.getId()))
        return false;
      int interval = connection.session.getMaxInactiveInterval();
      if (interval > 0
          && clock.millis() - connection.session.getLastAccessedTime() >= interval * 1000L)
        return false;
      Object stored =
          connection.session.getAttribute(
              HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
      if (!(stored instanceof SecurityContext context)
          || context.getAuthentication() == null
          || !context.getAuthentication().isAuthenticated()
          || !(context.getAuthentication().getPrincipal() instanceof LoginMember member)
          || !member.memberId().equals(connection.identity.memberId())
          || member.authVersion() == null
          || member.authVersion() != connection.identity.authVersion()) return false;
      return authVersionLookup
          .currentAuthVersion(member.memberId())
          .filter(version -> version == connection.identity.authVersion())
          .isPresent();
    } catch (RuntimeException unavailable) {
      return false;
    }
  }

  private static AccessDeniedException denied() {
    return new AccessDeniedException("FORBIDDEN");
  }
}
