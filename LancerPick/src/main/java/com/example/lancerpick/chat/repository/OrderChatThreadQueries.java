package com.example.lancerpick.chat.repository;

import com.example.lancerpick.chat.entity.QOrderChatMessage;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.member.entity.QMember;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.entity.QOrder;
import com.example.lancerpick.order.entity.QOrderSnapshot;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!demo")
public class OrderChatThreadQueries {
  private static final QOrder o = QOrder.order;
  private static final QOrderSnapshot s = QOrderSnapshot.orderSnapshot;
  private static final QMember other = new QMember("chatCounterparty");
  private static final QOrderChatMessage last = new QOrderChatMessage("lastChatMessage");
  private final JPAQueryFactory query;

  public OrderChatThreadQueries(EntityManager em) {
    query = new JPAQueryFactory(em);
  }

  public record Condition(long memberId, String keyword, boolean numberOnly, Long orderId) {}

  public record Row(
      long orderId,
      long buyerId,
      String productTitle,
      long counterpartyId,
      String counterpartyName,
      OrderStatus status,
      String lastContent,
      Long lastSenderId,
      LocalDateTime lastAt,
      LocalDateTime activityAt) {}

  private BooleanBuilder counterpart(long member) {
    return new BooleanBuilder(o.buyerId.eq(member).and(other.id.eq(o.sellerId)))
        .or(o.sellerId.eq(member).and(other.id.eq(o.buyerId)));
  }

  private BooleanBuilder where(Condition c) {
    var predicate = new BooleanBuilder(o.buyerId.eq(c.memberId()).or(o.sellerId.eq(c.memberId())));
    if (c.numberOnly()) {
      predicate.and(c.orderId() == null ? o.id.isNull() : o.id.eq(c.orderId()));
    } else if (c.keyword() != null) {
      var pattern =
          "%" + c.keyword().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
      predicate.and(
          s.productTitleSnapshot.like(pattern, '!').or(other.displayName.like(pattern, '!')));
    }
    return predicate;
  }

  public long count(Condition c) {
    return query
        .select(o.id.count())
        .from(o)
        .leftJoin(s)
        .on(s.orderId.eq(o.id))
        .join(other)
        .on(counterpart(c.memberId()))
        .where(where(c))
        .fetchOne();
  }

  public List<Row> find(Condition c, int size, long offset) {
    var activity = last.createdAt.coalesce(o.createdAt);
    return query
        .select(
            o.id,
            o.buyerId,
            o.status,
            o.chatSequence,
            o.createdAt,
            s.productTitleSnapshot,
            other.id,
            other.displayName,
            last.sequence,
            last.senderId,
            last.content,
            last.createdAt)
        .from(o)
        .leftJoin(s)
        .on(s.orderId.eq(o.id))
        .join(other)
        .on(counterpart(c.memberId()))
        .leftJoin(last)
        .on(last.orderId.eq(o.id).and(last.sequence.eq(o.chatSequence)))
        .where(where(c))
        .orderBy(activity.desc(), o.id.desc())
        .offset(offset)
        .limit(size)
        .fetch()
        .stream()
        .map(row -> {
          if (row.get(s.productTitleSnapshot) == null
              || (row.get(o.chatSequence) > 0 && row.get(last.sequence) == null)) {
            throw new BusinessException(
                HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY_ERROR", "주문 대화 기록을 확인해야 합니다.");
          }
          var lastAt = row.get(last.createdAt);
          return new Row(
              row.get(o.id),
              row.get(o.buyerId),
              row.get(s.productTitleSnapshot),
              row.get(other.id),
              row.get(other.displayName),
              row.get(o.status),
              row.get(last.content),
              row.get(last.senderId),
              lastAt,
              lastAt == null ? row.get(o.createdAt) : lastAt);
        })
        .toList();
  }
}
