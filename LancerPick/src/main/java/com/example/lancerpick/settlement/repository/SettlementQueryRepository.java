package com.example.lancerpick.settlement.repository;

import com.example.lancerpick.order.entity.QOrder;
import com.example.lancerpick.settlement.entity.QSettlement;
import com.example.lancerpick.settlement.entity.Settlement;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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

  public long count(long member) {
    return query
        .select(settlement.id.count())
        .from(settlement)
        .join(order)
        .on(order.id.eq(settlement.orderId))
        .where(order.sellerId.eq(member))
        .fetchOne();
  }

  public List<Settlement> list(long member, int size, long offset) {
    return query
        .selectFrom(settlement)
        .join(order)
        .on(order.id.eq(settlement.orderId))
        .where(order.sellerId.eq(member))
        .orderBy(settlement.createdAt.desc(), settlement.id.desc())
        .offset(offset)
        .limit(size)
        .fetch();
  }

  public record Totals(long count, BigDecimal gross, BigDecimal fee, BigDecimal seller) {}

  public Totals totals(long member) {
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
            .where(order.sellerId.eq(member))
            .fetchOne();
    return new Totals(
        result.get(settlement.id.count()), result.get(gross), result.get(fee), result.get(seller));
  }
}
