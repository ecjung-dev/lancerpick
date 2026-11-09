package com.example.lancerpick.admin.repository;

import com.example.lancerpick.admin.dto.AdminInquiryResponses.Party;
import com.example.lancerpick.admin.dto.AdminReviewTimerResponses.ActiveTimer;
import com.example.lancerpick.member.entity.QMember;
import com.example.lancerpick.order.entity.OrderResponseTimer;
import com.example.lancerpick.order.entity.QOrder;
import com.example.lancerpick.order.entity.QOrderResponseTimer;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 관리자 화면의 진행 중 타이머 목록(QueryDSL, 읽기 전용). 해제 자체는 ReviewTimerService가 주문 잠금 아래에서 한다. */
@Repository
@Profile("!demo")
public class AdminReviewTimerQueries {
    private static final QOrderResponseTimer t = QOrderResponseTimer.orderResponseTimer;
    private static final QOrder o = QOrder.order;
    private static final QMember buyer = new QMember("buyer");
    private static final QMember seller = new QMember("seller");
    private final JPAQueryFactory query;

    public AdminReviewTimerQueries(EntityManager em) {
        this.query = new JPAQueryFactory(em);
    }

    public long countActive() {
        Long total = query.select(t.id.count()).from(t).where(t.status.eq(OrderResponseTimer.ACTIVE)).fetchOne();
        return total == null ? 0 : total;
    }

    /** 진행 중이며 until(UTC) 이전에 만료되는 타이머 수. 이미 만료됐지만 처리 전인 것도 포함한다(운영 현황용). */
    public long countActiveExpiringBefore(LocalDateTime until) {
        Long total = query.select(t.id.count()).from(t)
                .where(t.status.eq(OrderResponseTimer.ACTIVE), t.expiresAt.lt(until)).fetchOne();
        return total == null ? 0 : total;
    }

    /** 만료 시각이 이른 순(먼저 자동 확정될 주문부터). 같은 시각은 타이머 ID 순. */
    public List<ActiveTimer> findActive(int size, long offset) {
        return query.select(t, buyer.id, buyer.displayName, seller.id, seller.displayName)
                .from(t)
                .join(o).on(o.id.eq(t.orderId))
                .join(buyer).on(buyer.id.eq(o.buyerId))
                .join(seller).on(seller.id.eq(o.sellerId))
                .where(t.status.eq(OrderResponseTimer.ACTIVE))
                .orderBy(t.expiresAt.asc(), t.id.asc())
                .limit(size)
                .offset(offset)
                .fetch()
                .stream()
                .map(row -> {
                    var timer = row.get(t);
                    return new ActiveTimer(
                            timer.getId().toString(),
                            Long.toString(timer.getOrderId()),
                            timer.getCycleNo(),
                            at(timer.getStartedAt()),
                            at(timer.getExpiresAt()),
                            new Party(row.get(buyer.id).toString(), row.get(buyer.displayName)),
                            new Party(row.get(seller.id).toString(), row.get(seller.displayName)));
                })
                .toList();
    }

    private static OffsetDateTime at(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
