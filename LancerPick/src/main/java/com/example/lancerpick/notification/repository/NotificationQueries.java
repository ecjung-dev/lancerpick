package com.example.lancerpick.notification.repository;

import com.example.lancerpick.notification.dto.NotificationResponse;
import com.example.lancerpick.notification.entity.QNotification;
import com.example.lancerpick.order.entity.QOrderSnapshot;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!demo")
public class NotificationQueries {
  private final JPAQueryFactory query;

  public NotificationQueries(EntityManager em) {
    query = new JPAQueryFactory(em);
  }

  /** 본인 알림만 최신순으로 조회한다. 상품명은 현재 상품이 아닌 주문 시점 스냅샷을 사용한다. */
  public List<NotificationResponse> find(long recipientId, int size, long offset) {
    var n = QNotification.notification;
    var s = QOrderSnapshot.orderSnapshot;
    return query
        .select(n, s.productTitleSnapshot)
        .from(n)
        .leftJoin(s)
        .on(s.orderId.eq(n.orderId))
        .where(n.recipientId.eq(recipientId))
        .orderBy(n.createdAt.desc(), n.id.desc())
        .limit(size)
        .offset(offset)
        .fetch()
        .stream()
        .map(tuple -> {
          var notification = tuple.get(n);
          return new NotificationResponse(
              notification.getId().toString(),
              notification.getType(),
              notification.getOrderId() == null ? null : notification.getOrderId().toString(),
              tuple.get(s.productTitleSnapshot),
              notification.getReadAt() != null,
              notification.getCreatedAt().atOffset(ZoneOffset.UTC));
        })
        .toList();
  }
}
