package com.example.lancerpick.chat.repository;

import com.example.lancerpick.chat.entity.OrderChatMessage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderChatMessageRepository extends JpaRepository<OrderChatMessage, Long> {
  Optional<OrderChatMessage> findByOrderIdAndSenderIdAndClientMessageId(
      long orderId, long senderId, String clientMessageId);
}
