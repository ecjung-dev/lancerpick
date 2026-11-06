package com.example.lancerpick.chat.config;

import com.example.lancerpick.chat.realtime.ChatHandshake;
import com.example.lancerpick.chat.realtime.ChatInboundSecurity;
import com.example.lancerpick.chat.realtime.ChatSocketErrorHandler;
import com.example.lancerpick.chat.realtime.ChatSocketSessions;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.messaging.web.socket.server.CsrfTokenHandshakeInterceptor;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@Profile("!demo")
@EnableWebSocketMessageBroker
@EnableScheduling
@RequiredArgsConstructor
public class ChatWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
  private final ChatSocketSessions sessions;
  private final JsonMapper jsonMapper;

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    var handshake = new ChatHandshake();
    registry
        .addEndpoint("/ws")
        .setHandshakeHandler(handshake)
        .addInterceptors(handshake, new CsrfTokenHandshakeInterceptor());
    registry.setErrorHandler(new ChatSocketErrorHandler()).setPreserveReceiveOrder(true);
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.setApplicationDestinationPrefixes("/app");
    registry.setUserDestinationPrefix("/user");
    registry.setPreservePublishOrder(true);
    registry
        .enableSimpleBroker("/queue")
        .setTaskScheduler(chatHeartbeatScheduler())
        .setHeartbeatValue(new long[] {10000, 10000});
  }

  @Bean
  public ThreadPoolTaskScheduler chatHeartbeatScheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("chat-heartbeat-");
    return scheduler;
  }

  @Bean
  public HttpSessionEventPublisher chatSessionEventPublisher() {
    return new HttpSessionEventPublisher();
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.taskExecutor().corePoolSize(2).maxPoolSize(2).queueCapacity(100);
    registration.interceptors(new ChatInboundSecurity(sessions));
  }

  @Override
  public void configureClientOutboundChannel(ChannelRegistration registration) {
    registration.taskExecutor().corePoolSize(2).maxPoolSize(2).queueCapacity(100);
    registration.interceptors(
        new ChannelInterceptor() {
          @Override
          public Message<?> preSend(Message<?> message, MessageChannel channel) {
            return sessions.allowOutbound(
                    SimpMessageHeaderAccessor.getSessionId(message.getHeaders()))
                ? message
                : null;
          }
        });
  }

  @Override
  public boolean configureMessageConverters(List<MessageConverter> converters) {
    converters.add(new JacksonJsonMessageConverter(jsonMapper));
    return true;
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration
        .setMessageSizeLimit(16384)
        .setSendBufferSizeLimit(65536)
        .setSendTimeLimit(10000)
        .setTimeToFirstMessage(10000);
    registration.addDecoratorFactory(handler ->
        new WebSocketHandlerDecorator(handler) {
          @Override
          public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            session.setTextMessageSizeLimit(16384);
            sessions.register(session);
            if (session.isOpen()) super.afterConnectionEstablished(session);
          }

          @Override
          public void handleMessage(WebSocketSession session, WebSocketMessage<?> message)
              throws Exception {
            if (sessions.allowFrame(session.getId())) super.handleMessage(session, message);
          }

          @Override
          public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
              throws Exception {
            sessions.remove(session.getId());
            super.afterConnectionClosed(session, status);
          }
        });
  }
}
