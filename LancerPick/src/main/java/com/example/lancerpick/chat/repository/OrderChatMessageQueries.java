package com.example.lancerpick.chat.repository;

import com.example.lancerpick.chat.entity.OrderChatMessage;
import com.example.lancerpick.chat.entity.QOrderChatMessage;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!demo")
public class OrderChatMessageQueries {
  private final JPAQueryFactory queryFactory;

  public OrderChatMessageQueries(EntityManager entityManager) {
    this.queryFactory = new JPAQueryFactory(entityManager);
  }

  public List<OrderChatMessage> find(
      long orderId, Long beforeSequence, Long afterSequence, int limit) {
    var message = QOrderChatMessage.orderChatMessage;
    var query = queryFactory.selectFrom(message).where(message.orderId.eq(orderId));
    if (afterSequence != null) {
      return query
          .where(message.sequence.gt(afterSequence))
          .orderBy(message.sequence.asc())
          .limit(limit)
          .fetch();
    }
    if (beforeSequence != null) {
      query.where(message.sequence.lt(beforeSequence));
    }
    return query.orderBy(message.sequence.desc()).limit(limit).fetch();
  }
}
