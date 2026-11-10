package com.example.lancerpick.settlement.repository;

import com.example.lancerpick.member.entity.QMember;
import com.example.lancerpick.order.entity.QOrder;
import com.example.lancerpick.order.entity.QOrderSnapshot;
import com.example.lancerpick.payment.entity.PaymentStatus;
import com.example.lancerpick.payment.entity.QPayment;
import com.example.lancerpick.settlement.entity.QSettlement;
import com.example.lancerpick.settlement.entity.Settlement;
import com.example.lancerpick.wallet.entity.QWalletEntry;
import com.example.lancerpick.wallet.entity.WalletEntryType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
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
  private static final QOrderSnapshot snapshot = QOrderSnapshot.orderSnapshot;
  private static final QMember buyer = new QMember("settlementBuyer");

  private final JPAQueryFactory query;

  public SettlementQueryRepository(EntityManager entityManager) {
    query = new JPAQueryFactory(entityManager);
  }

  public record Condition(
      long member,
      LocalDateTime from,
      LocalDateTime until,
      boolean oldest,
      String keyword,
      boolean numeric,
      Long orderId) {}

  public record View(
      Settlement value,
      String title,
      String buyerId,
      String buyerName,
      String policyVersion,
      boolean credited) {}

  private BooleanBuilder owned(Condition condition) {
    var predicate = new BooleanBuilder(order.sellerId.eq(condition.member()));
    if (condition.from() != null) {
      predicate.and(settlement.createdAt.goe(condition.from()));
    }
    if (condition.until() != null) {
      predicate.and(settlement.createdAt.lt(condition.until()));
    }
    if (condition.numeric()) {
      predicate.and(
          condition.orderId() == null ? order.id.isNull() : order.id.eq(condition.orderId()));
    } else if (condition.keyword() != null) {
      String literal =
          "%" + condition.keyword().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
      predicate.and(snapshot.productTitleSnapshot.like(literal, '!'));
    }
    return predicate;
  }

  public long count(Condition condition) {
    return query
        .select(settlement.id.count())
        .from(settlement)
        .join(order)
        .on(order.id.eq(settlement.orderId))
        .leftJoin(snapshot)
        .on(snapshot.orderId.eq(order.id))
        .where(owned(condition))
        .fetchOne();
  }

  public List<View> list(Condition condition, int size, long offset) {
    var entry = new QWalletEntry("settlementPayout");
    var payment = new QPayment("settlementPayment");
    var credited =
        JPAExpressions.selectOne()
            .from(entry)
            .join(payment)
            .on(payment.id.eq(entry.paymentId))
            .where(
                payment.orderId.eq(order.id),
                payment.status.eq(PaymentStatus.PAID),
                payment.amount.eq(settlement.grossAmount),
                payment.orderPolicyVersion.eq(order.policyVersion),
                entry.memberId.eq(order.sellerId),
                entry.type.eq(WalletEntryType.SELLER_PAYOUT),
                entry.amountDelta.eq(settlement.sellerAmount))
            .exists();
    return query
        .select(
            settlement,
            snapshot.productTitleSnapshot,
            buyer.id,
            buyer.displayName,
            order.policyVersion,
            credited)
        .from(settlement)
        .join(order)
        .on(order.id.eq(settlement.orderId))
        .leftJoin(snapshot)
        .on(snapshot.orderId.eq(order.id))
        .leftJoin(buyer)
        .on(buyer.id.eq(order.buyerId))
        .where(owned(condition))
        .orderBy(
            condition.oldest() ? settlement.createdAt.asc() : settlement.createdAt.desc(),
            condition.oldest() ? settlement.id.asc() : settlement.id.desc())
        .offset(offset)
        .limit(size)
        .fetch()
        .stream()
        .map(row ->
            new View(
                row.get(settlement),
                row.get(snapshot.productTitleSnapshot),
                row.get(buyer.id).toString(),
                row.get(buyer.displayName),
                row.get(order.policyVersion),
                Boolean.TRUE.equals(row.get(credited))))
        .toList();
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
            .leftJoin(snapshot)
            .on(snapshot.orderId.eq(order.id))
            .where(owned(condition))
            .fetchOne();
    return new Totals(
        result.get(settlement.id.count()), result.get(gross), result.get(fee), result.get(seller));
  }
}
