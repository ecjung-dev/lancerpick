package com.example.lancerpick.chat.realtime;

import com.example.lancerpick.common.security.LoginMember;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

public class ChatHandshake extends DefaultHandshakeHandler implements HandshakeInterceptor {
  private static final String IDENTITY = ChatHandshake.class.getName() + ".identity";

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Map<String, Object> attributes) {
    if (!(request instanceof ServletServerHttpRequest servletRequest)
        || !(request.getPrincipal() instanceof Authentication authentication)
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof LoginMember member)
        || member.authVersion() == null) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }
    var session = servletRequest.getServletRequest().getSession(false);
    if (session == null) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }
    attributes.put(ChatSocketSessions.HTTP_SESSION, session);
    attributes.put(ChatSocketSessions.HTTP_SESSION_ID, session.getId());
    var identity =
        new ChatSocketIdentity(
            member.memberId(), member.authVersion(), UUID.randomUUID().toString());
    attributes.put(
        IDENTITY,
        UsernamePasswordAuthenticationToken.authenticated(
            identity, null, authentication.getAuthorities()));
    return true;
  }

  @Override
  protected Principal determineUser(
      ServerHttpRequest request, WebSocketHandler handler, Map<String, Object> attributes) {
    return (Principal) attributes.get(IDENTITY);
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Exception failure) {}
}
