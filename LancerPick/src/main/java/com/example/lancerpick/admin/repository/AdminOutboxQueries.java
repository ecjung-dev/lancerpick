package com.example.lancerpick.admin.repository;

import com.example.lancerpick.admin.dto.AdminOutboxResponses.OutboxIssue;
import com.example.lancerpick.notification.entity.OutboxEvent;
import com.example.lancerpick.notification.entity.QOutboxEvent;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * 관리자 화면의 알림 처리 실패·재시도 대기 목록(QueryDSL, 읽기 전용, 행 잠금 없음).
 * 처리기(OutboxDispatcher)는 이벤트 행을 잠근 뒤 상태를 다시 확인하므로 이 조회가 처리와 경합하지 않는다.
 */
@Repository
@Profile("!demo")
public class AdminOutboxQueries {
    /** 조회 조건. FAILED = 자동 재시도 멈춤, RETRYING = 실패 후 다음 시도 대기. */
    public enum Issue { FAILED, RETRYING }

    private static final QOutboxEvent e = QOutboxEvent.outboxEvent;
    private static final String ORDER = "ORDER";
    private final JPAQueryFactory query;

    public AdminOutboxQueries(EntityManager em) {
        this.query = new JPAQueryFactory(em);
    }

    public long count(Issue issue, Long orderId) {
        Long total = query.select(e.id.count()).from(e).where(condition(issue), aggregate(orderId)).fetchOne();
        return total == null ? 0 : total;
    }

    /** 최근 이벤트부터(ID 내림차순). */
    public List<OutboxIssue> find(Issue issue, Long orderId, int size, long offset) {
        return query.selectFrom(e)
                .where(condition(issue), aggregate(orderId))
                .orderBy(e.id.desc())
                .limit(size)
                .offset(offset)
                .fetch()
                .stream()
                .map(event -> new OutboxIssue(
                        event.getId().toString(),
                        event.getEventId(),
                        event.getType(),
                        event.getAggregateType(),
                        Long.toString(event.getAggregateId()),
                        OutboxEvent.FAILED.equals(event.getStatus()) ? Issue.FAILED.name() : Issue.RETRYING.name(),
                        event.getAttemptCount(),
                        event.getLastErrorCode(),
                        // FAILED는 다시 시도하지 않으므로 다음 시도 시각을 보여 주지 않는다.
                        OutboxEvent.FAILED.equals(event.getStatus()) ? null : at(event.getNextAttemptAt()),
                        at(event.getCreatedAt())))
                .toList();
    }

    private static BooleanExpression condition(Issue issue) {
        return issue == Issue.FAILED
                ? e.status.eq(OutboxEvent.FAILED)
                : e.status.eq(OutboxEvent.PENDING).and(e.attemptCount.gt(0));
    }

    /** 주문 번호 조건. 집계 유형이 다르면 같은 숫자 ID라도 다른 대상이므로 ORDER로 한정한다. */
    private static BooleanExpression aggregate(Long orderId) {
        return orderId == null ? null : e.aggregateType.eq(ORDER).and(e.aggregateId.eq(orderId));
    }

    private static OffsetDateTime at(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
