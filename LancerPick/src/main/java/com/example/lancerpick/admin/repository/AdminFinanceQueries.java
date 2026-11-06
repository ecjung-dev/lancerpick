package com.example.lancerpick.admin.repository;

import com.example.lancerpick.admin.dto.AdminFinanceResponses;
import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.member.entity.MemberRole;
import com.example.lancerpick.member.entity.QMember;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.QOrder;
import com.example.lancerpick.order.entity.QOrderClosure;
import com.example.lancerpick.payment.entity.Payment;
import com.example.lancerpick.payment.entity.QOrderFunds;
import com.example.lancerpick.payment.entity.QPayment;
import com.example.lancerpick.settlement.entity.QSettlement;
import com.example.lancerpick.wallet.entity.QWalletTopup;
import com.example.lancerpick.wallet.entity.WalletTopupStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 업무 데이터는 QueryDSL로 읽는다. 변경 쿼리나 관리자 전용 금액 갱신을 제공하지 않는다. */
@Repository
@Profile("!demo")
public class AdminFinanceQueries {
  private static final QWalletTopup t = QWalletTopup.walletTopup;
  private static final QPayment p = QPayment.payment;
  private static final QOrder o = QOrder.order;
  private static final QOrderFunds f = QOrderFunds.orderFunds;
  private static final QSettlement s = QSettlement.settlement;
  private static final QOrderClosure c = QOrderClosure.orderClosure;
  private final JPAQueryFactory query;

  public AdminFinanceQueries(EntityManager entityManager) {
    query = new JPAQueryFactory(entityManager);
  }

  public record TopupFilter(Long memberId, WalletTopupStatus status) {}

  public record PaymentFilter(
      Long orderId, Long buyerId, Long sellerId, String status, TradePolicyVersion policyVersion) {}

  public record TopupRow(
      long id,
      long memberId,
      long amount,
      WalletTopupStatus status,
      String lastErrorCode,
      int attempts,
      Instant createdAt,
      Instant processingStartedAt,
      Instant nextRecoveryAt,
      Instant leaseUntil,
      Instant completedAt,
      boolean identified) {}

  public Optional<MemberRole> memberRole(long id) {
    var m = QMember.member;
    return Optional.ofNullable(query.select(m.role).from(m).where(m.id.eq(id)).fetchOne());
  }

  public long countTopups(TopupFilter filter) {
    return query.select(t.id.count()).from(t).where(topupWhere(filter)).fetchOne();
  }

  public List<TopupRow> topups(TopupFilter filter, long offset, int size) {
    return topupRows(filter, null, offset, size);
  }

  public Optional<TopupRow> topup(long id) {
    return topupRows(new TopupFilter(null, null), id, 0, 1).stream().findFirst();
  }

  private List<TopupRow> topupRows(TopupFilter filter, Long id, long offset, int size) {
    var where = topupWhere(filter);
    if (id != null) where.and(t.id.eq(id));
    return query
        .select(
            t.id,
            t.memberId,
            t.amount,
            t.status,
            t.lastErrorCode,
            t.recoveryAttemptCount,
            t.createdAt,
            t.processingStartedAt,
            t.nextRecoveryAt,
            t.recoveryLeaseUntil,
            t.completedAt,
            t.paymentKey.isNotNull())
        .from(t)
        .where(where)
        .orderBy(t.createdAt.desc(), t.id.desc())
        .offset(offset)
        .limit(size)
        .fetch()
        .stream()
        .map(row ->
            new TopupRow(
                row.get(t.id),
                row.get(t.memberId),
                row.get(t.amount),
                row.get(t.status),
                row.get(t.lastErrorCode),
                row.get(t.recoveryAttemptCount),
                row.get(t.createdAt),
                row.get(t.processingStartedAt),
                row.get(t.nextRecoveryAt),
                row.get(t.recoveryLeaseUntil),
                row.get(t.completedAt),
                Boolean.TRUE.equals(row.get(t.paymentKey.isNotNull()))))
        .toList();
  }

  public long countPayments(PaymentFilter filter) {
    return query
        .select(p.id.count())
        .from(p)
        .join(o)
        .on(o.id.eq(p.orderId))
        .where(paymentWhere(filter))
        .fetchOne();
  }

  public List<AdminFinanceResponses.PaymentRecord> payments(
      PaymentFilter filter, long offset, int size) {
    return query
        .select(p, o)
        .from(p)
        .join(o)
        .on(o.id.eq(p.orderId))
        .where(paymentWhere(filter))
        .orderBy(p.paidAt.desc(), p.id.desc())
        .offset(offset)
        .limit(size)
        .fetch()
        .stream()
        .map(row -> payment(row.get(p), row.get(o)))
        .toList();
  }

  public Optional<AdminFinanceResponses.OrderFinance> orderFinance(long id) {
    var row =
        query
            .select(o, p, f, s, c)
            .from(o)
            .leftJoin(p)
            .on(p.orderId.eq(o.id))
            .leftJoin(f)
            .on(f.orderId.eq(o.id))
            .leftJoin(s)
            .on(s.orderId.eq(o.id))
            .leftJoin(c)
            .on(c.orderId.eq(o.id))
            .where(o.id.eq(id))
            .fetchOne();
    if (row == null) return Optional.empty();
    var order = row.get(o);
    var funds = row.get(f);
    var settlement = row.get(s);
    var closure = row.get(c);
    return Optional.of(
        new AdminFinanceResponses.OrderFinance(
            order.getId().toString(),
            order.getBuyerId().toString(),
            order.getSellerId().toString(),
            order.getPolicyVersion(),
            order.getStatus(),
            order.getPriceAmount(),
            payment(row.get(p), order),
            funds == null
                ? null
                : new AdminFinanceResponses.Funds(
                    Long.toString(funds.getPaymentId()),
                    funds.getStatus(),
                    funds.getAmount(),
                    funds.getRemainingAmount(),
                    at(funds.getHeldAt()),
                    at(funds.getResolvedAt())),
            settlement == null
                ? null
                : new AdminFinanceResponses.SettlementRecord(
                    settlement.getId().toString(),
                    settlement.getSettlementType(),
                    settlement.getGrossAmount(),
                    settlement.getFeeRateBps(),
                    settlement.getFeeAmount(),
                    settlement.getSellerAmount(),
                    at(settlement.getCreatedAt())),
            closure == null
                ? null
                : new AdminFinanceResponses.Closure(
                    closure.getReason(),
                    closure.getPaidAmount(),
                    closure.getBuyerRefundAmount(),
                    closure.getSellerGrossAmount(),
                    closure.getFeeAmount(),
                    closure.getSellerNetAmount())));
  }

  private static AdminFinanceResponses.PaymentRecord payment(Payment payment, Order order) {
    if (payment == null) return null;
    return new AdminFinanceResponses.PaymentRecord(
        Long.toString(payment.id()),
        order.getId().toString(),
        order.getBuyerId().toString(),
        order.getSellerId().toString(),
        payment.getOrderPolicyVersion(),
        order.getStatus(),
        payment.status(),
        payment.amount(),
        payment.getRefundedAmount(),
        at(payment.getPaidAt()),
        at(payment.getRefundedAt()));
  }

  private static BooleanBuilder topupWhere(TopupFilter filter) {
    var where = new BooleanBuilder();
    if (filter.memberId() != null) where.and(t.memberId.eq(filter.memberId()));
    if (filter.status() != null) where.and(t.status.eq(filter.status()));
    return where;
  }

  private static BooleanBuilder paymentWhere(PaymentFilter filter) {
    var where = new BooleanBuilder();
    if (filter.orderId() != null) where.and(o.id.eq(filter.orderId()));
    if (filter.buyerId() != null) where.and(o.buyerId.eq(filter.buyerId()));
    if (filter.sellerId() != null) where.and(o.sellerId.eq(filter.sellerId()));
    if (filter.status() != null) where.and(p.status.eq(filter.status()));
    if (filter.policyVersion() != null)
      where.and(o.policyVersion.eq(filter.policyVersion().name()));
    return where;
  }

  private static OffsetDateTime at(LocalDateTime value) {
    return value == null ? null : value.atOffset(ZoneOffset.UTC);
  }
}
