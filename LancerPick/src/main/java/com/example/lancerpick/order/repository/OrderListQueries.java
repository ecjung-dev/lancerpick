package com.example.lancerpick.order.repository;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.member.entity.QMember;
import com.example.lancerpick.order.dto.OrderDetailResponse.Participant;
import com.example.lancerpick.order.dto.OrderSide;
import com.example.lancerpick.order.dto.OrderSummaryResponse;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.entity.QOrder;
import com.example.lancerpick.order.entity.QOrderSnapshot;
import com.example.lancerpick.order.entity.QRevisionRequest;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!demo")
public class OrderListQueries {
    private static final QOrder o = QOrder.order;
    private static final QOrderSnapshot s = QOrderSnapshot.orderSnapshot;
    private static final QMember m = QMember.member;
    private static final QRevisionRequest r = QRevisionRequest.revisionRequest;
    private final JPAQueryFactory query;

    public OrderListQueries(EntityManager em) {
        query = new JPAQueryFactory(em);
    }

    /**
     * 목록 조건. keyword는 상품명 스냅샷·상대방 표시 이름 부분 일치, orderNumberOnly이면 orderId 정확 일치만 본다
     * (orderId가 null이면 일치하는 주문 없음). 기간은 UTC 저장 시각의 [createdFrom, createdBefore) 구간이다.
     */
    public record Condition(
            long memberId,
            OrderSide side,
            OrderStatus status,
            String keyword,
            boolean orderNumberOnly,
            Long orderId,
            LocalDateTime createdFrom,
            LocalDateTime createdBefore,
            boolean oldestFirst) {}

    // 건수와 목록이 같은 조인·조건을 쓰도록 상대방·스냅샷 조인을 함께 둔다.
    private BooleanBuilder predicate(Condition c) {
        var where = new BooleanBuilder((c.side() == OrderSide.BUYER ? o.buyerId : o.sellerId).eq(c.memberId()));
        if (c.status() != null) where.and(o.status.eq(c.status()));
        if (c.orderNumberOnly()) {
            where.and(c.orderId() == null ? Expressions.FALSE.isTrue() : o.id.eq(c.orderId()));
        } else if (c.keyword() != null) {
            String pattern = "%" + c.keyword().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            where.and(s.productTitleSnapshot.like(pattern, '!').or(m.displayName.like(pattern, '!')));
        }
        if (c.createdFrom() != null) where.and(o.createdAt.goe(c.createdFrom()));
        if (c.createdBefore() != null) where.and(o.createdAt.lt(c.createdBefore()));
        return where;
    }

    private Predicate counterparty(Condition c) {
        return m.id.eq(c.side() == OrderSide.BUYER ? o.sellerId : o.buyerId);
    }

    public long count(Condition c) {
        Long total = query.select(o.id.count()).from(o)
                .leftJoin(s).on(s.orderId.eq(o.id))
                .join(m).on(counterparty(c))
                .where(predicate(c)).fetchOne();
        return total == null ? 0 : total;
    }

    public List<OrderSummaryResponse> find(Condition c, int size, long offset) {
        var orderBy = c.oldestFirst()
                ? new OrderSpecifier<?>[] {o.createdAt.asc(), o.id.asc()}
                : new OrderSpecifier<?>[] {o.createdAt.desc(), o.id.desc()};
        return query.select(o, s, m, r).from(o)
                .leftJoin(s).on(s.orderId.eq(o.id))
                .join(m).on(counterparty(c))
                .leftJoin(r).on(r.orderId.eq(o.id))
                .where(predicate(c)).orderBy(orderBy).limit(size).offset(offset).fetch().stream().map(tuple -> {
                    var order = tuple.get(o);
                    var snapshot = tuple.get(s);
                    var other = tuple.get(m);
                    var revision = tuple.get(r);
                    if (snapshot == null)
                        throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY_ERROR", "주문 조건 기록을 확인해야 합니다.");
                    LocalDateTime due = order.getPaidAt() == null ? null : revision != null ? revision.getDueAt() : order.getDueAt();
                    return new OrderSummaryResponse(order.getId().toString(), order.getStatus().name(),
                            snapshot.getProductTitleSnapshot(), order.getPriceAmount(),
                            new Participant(other.getId().toString(), other.getDisplayName()),
                            order.getCreatedAt().atOffset(ZoneOffset.UTC),
                            due == null ? null : due.atOffset(ZoneOffset.UTC), order.getPolicyVersion());
                }).toList();
    }
}
