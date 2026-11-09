package com.example.lancerpick.settlement.repository;

import com.example.lancerpick.order.entity.QOrder;
import com.example.lancerpick.settlement.entity.QSettlement;
import com.example.lancerpick.settlement.entity.Settlement;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 본인 판매 주문 조건과 안정적인 정렬을 QueryDSL로 구성한다. */
@Repository
@Profile("!demo")
public class SettlementQueryRepository {
  private static final QSettlement settlement = QSettlement.settlement;
  private static final QOrder order = QOrder.order;

  private final JPAQueryFactory query;

  public SettlementQueryRepository(EntityManager entityManager) {
    query = new JPAQueryFactory(entityManager);
  }

  public record Condition(long member, LocalDateTime from, LocalDateTime until, boolean oldest) {}

  private BooleanBuilder owned(Condition condition) {
    var predicate = new BooleanBuilder(order.sellerId.eq(condition.member()));
    if (condition.from() != null) {
      predicate.and(settlement.createdAt.goe(condition.from()));
    }
    if (condition.until() != null) {
      predicate.and(settlement.createdAt.lt(condition.until()));
    }
    return predicate;
  }

  public long count(Condition condition) {
    return query
        .select(settlement.id.count())
        .from(settlement)
        .join(order)
        .on(order.id.eq(settlement.orderId))
        .where(owned(condition))
        .fetchOne();
  }

  public List<Settlement> list(Condition condition, int size, long offset) {
    return query
        .selectFrom(settlement)
        .join(order)
        .on(order.id.eq(settlement.orderId))
        .where(owned(condition))
        .orderBy(
            condition.oldest() ? settlement.createdAt.asc() : settlement.createdAt.desc(),
            condition.oldest() ? settlement.id.asc() : settlement.id.desc())
        .offset(offset)
        .limit(size)
        .fetch();
  }

  public record Totals(long count, BigDecimal gross, BigDecimal fee, BigDecimal seller) {}

  public Totals totals(Condition condition) {
    // SUM의 큰 정밀도를 Long으로 먼저 축소하지 않고 API 경계에서 정확 변환한다.
    var gross =
        Expressions.numberTemplate(
                BigDecimal.class, "sum(cast({0} as BigDecimal))", settlement.grossAmount)
            .coalesce(BigDecimal.ZERO);
    var fee =
        Expressions.numberTemplate(
                BigDecimal.class, "sum(cast({0} as BigDecimal))", settlement.feeAmount)
            .coalesce(BigDecimal.ZERO);
    var seller =
        Expressions.numberTemplate(
                BigDecimal.class, "sum(cast({0} as BigDecimal))", settlement.sellerAmount)
            .coalesce(BigDecimal.ZERO);
    var result =
        query
            .select(settlement.id.count(), gross, fee, seller)
            .from(settlement)
            .join(order)
            .on(order.id.eq(settlement.orderId))
            .where(owned(condition))
            .fetchOne();
    return new Totals(
        result.get(settlement.id.count()), result.get(gross), result.get(fee), result.get(seller));
  }
}
