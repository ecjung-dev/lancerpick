package com.example.lancerpick.chat.service;

import com.example.lancerpick.chat.dto.ChatMessageResponse;
import com.example.lancerpick.chat.dto.ChatMessagesResponse;
import com.example.lancerpick.chat.dto.SendChatMessageRequest;
import com.example.lancerpick.chat.entity.OrderChatMessage;
import com.example.lancerpick.chat.event.ChatMessageStored;
import com.example.lancerpick.chat.repository.OrderChatMessageQueries;
import com.example.lancerpick.chat.repository.OrderChatMessageRepository;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.text.TextNormalizer;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.repository.OrderRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")
@RequiredArgsConstructor
public class OrderChatService {
  private static final Set<OrderStatus> WRITABLE_STATUSES =
      Set.of(
          OrderStatus.REQUESTED,
          OrderStatus.AWAITING_CONTRACT,
          OrderStatus.AWAITING_PAYMENT,
          OrderStatus.IN_PROGRESS,
          OrderStatus.AWAITING_CONFIRMATION,
          OrderStatus.REVISION_IN_PROGRESS,
          OrderStatus.AWAITING_BALANCE);
  private final OrderRepository orderRepository;
  private final OrderChatMessageRepository messageRepository;
  private final OrderChatMessageQueries messageQueries;
  private final Clock clock;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ChatMessagesResponse list(
      long memberId, long orderId, Long beforeSequence, Long afterSequence, int limit) {
    if (orderId < 1
        || limit < 1
        || limit > 100
        || (beforeSequence != null && beforeSequence < 1)
        || (afterSequence != null && afterSequence < 0)
        || (beforeSequence != null && afterSequence != null)) {
      throw invalid();
    }
    var order =
        orderRepository
            .findById(orderId)
            .filter(value -> isParticipant(value, memberId))
            .orElseThrow(OrderChatService::notFound);
    var fetched = messageQueries.find(orderId, beforeSequence, afterSequence, limit + 1);
    boolean hasMore = fetched.size() > limit;
    var selected = new ArrayList<>(fetched.subList(0, Math.min(limit, fetched.size())));
    if (afterSequence == null) {
      Collections.reverse(selected);
    }
    String nextCursor = null;
    if (hasMore) {
      nextCursor =
          Long.toString(
              afterSequence == null
                  ? selected.getFirst().getSequence()
                  : selected.getLast().getSequence());
    }
    return new ChatMessagesResponse(
        selected.stream().map(ChatMessageResponse::from).toList(),
        afterSequence == null ? "BEFORE" : "AFTER",
        nextCursor,
        hasMore,
        Long.toString(order.getChatSequence()),
        canSend(order.getStatus()));
  }

  /** 회원 공유 잠금 → 주문 배타 잠금. 순번 증가와 메시지를 한 트랜잭션에 저장하며 외부 전달은 하지 않는다. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Sent send(long memberId, long orderId, SendChatMessageRequest request) {
    if (orderId < 1
        || request == null
        || request.content() == null
        || request.clientMessageId() == null) {
      throw invalid();
    }
    String content = TextNormalizer.normalize(request.content());
    if (content.isEmpty()
        || content.codePointCount(0, content.length()) > 2000
        || !request
            .clientMessageId()
            .matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
      throw invalid();
    }
    String clientMessageId = UUID.fromString(request.clientMessageId()).toString();
    if (orderRepository.lockDecisionActor(memberId).isEmpty()) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    }
    var order =
        orderRepository.findForDecision(orderId, memberId).orElseThrow(OrderChatService::notFound);
    var existing =
        messageRepository.findByOrderIdAndSenderIdAndClientMessageId(
            orderId, memberId, clientMessageId);
    if (existing.isPresent()) {
      if (!existing.get().getContent().equals(content)) {
        throw new BusinessException(
            HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "다른 메시지에 사용한 요청 키입니다.");
      }
      return new Sent(ChatMessageResponse.from(existing.get()), false);
    }
    if (!canSend(order.getStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, "CHAT_CLOSED", "종료된 주문의 대화는 조회만 할 수 있습니다.");
    }
    var occurredAt =
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    if (occurredAt.getYear() < 1000
        || occurredAt.getYear() > 9999
        || occurredAt.isBefore(order.getCreatedAt())) {
      throw new IllegalStateException("Invalid chat message clock");
    }
    long sequence = order.nextChatSequence();
    var message =
        messageRepository.saveAndFlush(
            OrderChatMessage.create(
                orderId, sequence, memberId, clientMessageId, content, occurredAt));
    eventPublisher.publishEvent(
        new ChatMessageStored(
            ChatMessageResponse.from(message), order.getBuyerId(), order.getSellerId()));
    return new Sent(ChatMessageResponse.from(message), true);
  }

  private static boolean isParticipant(Order order, long memberId) {
    return order.getBuyerId() == memberId || order.getSellerId() == memberId;
  }

  static boolean canSend(OrderStatus status) {
    return WRITABLE_STATUSES.contains(status);
  }

  private static BusinessException notFound() {
    return new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "주문 대화를 찾을 수 없습니다.");
  }

  private static BusinessException invalid() {
    return new BusinessException(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "메시지와 조회 조건을 확인해 주세요.");
  }

  public record Sent(ChatMessageResponse message, boolean created) {}
}
