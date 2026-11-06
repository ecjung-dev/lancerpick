package com.example.lancerpick.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 주문별 순번으로 정렬하는 일반 텍스트 메시지. 저장 후 수정·삭제하지 않는다. */
@Getter
@Entity
@Table(name = "order_chat_messages")
public class OrderChatMessage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false, updatable = false)
  private long orderId;

  @Column(nullable = false, updatable = false)
  private long sequence;

  @Column(name = "sender_id", nullable = false, updatable = false)
  private long senderId;

  @Column(name = "client_message_id", nullable = false, updatable = false, length = 36)
  private String clientMessageId;

  @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
  private String content;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected OrderChatMessage() {}

  public static OrderChatMessage create(
      long orderId,
      long sequence,
      long senderId,
      String clientMessageId,
      String content,
      LocalDateTime createdAt) {
    if (orderId < 1
        || sequence < 1
        || senderId < 1
        || clientMessageId == null
        || content == null
        || content.isBlank()
        || content.codePointCount(0, content.length()) > 2000
        || createdAt == null) {
      throw new IllegalArgumentException("Invalid chat message");
    }
    var message = new OrderChatMessage();
    message.orderId = orderId;
    message.sequence = sequence;
    message.senderId = senderId;
    message.clientMessageId = clientMessageId;
    message.content = content;
    message.createdAt = createdAt;
    return message;
  }
}
