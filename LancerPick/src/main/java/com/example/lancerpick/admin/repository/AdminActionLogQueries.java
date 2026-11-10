package com.example.lancerpick.admin.repository;

import com.example.lancerpick.admin.dto.AdminActionReceipt;
import com.example.lancerpick.admin.entity.AdminAction;
import com.example.lancerpick.admin.entity.QAdminActionLog;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * K6 관리자 작업 감사 이력 목록(QueryDSL, 읽기 전용, 행 잠금 없음).
 * 대상 조건은 V13의 ix_admin_action_target(target_type, target_id, id), 주문 조건은 ix_admin_action_order(order_id, id)를 쓴다.
 * 실행 중인 요청을 막거나 바꾸지 않으므로 재처리 조율(AdminActionAuditService)과 경합하지 않는다.
 */
@Repository
@Profile("!demo")
public class AdminActionLogQueries {
    /** 목록 조건. 대상 번호는 명령(=대상 종류)과 함께만 의미가 있다. */
    public record Filter(AdminAction action, Long targetId, Long orderId) {}

    private static final QAdminActionLog a = QAdminActionLog.adminActionLog;
    private final JPAQueryFactory query;

    public AdminActionLogQueries(EntityManager em) {
        this.query = new JPAQueryFactory(em);
    }

    public long count(Filter filter) {
        Long total = query.select(a.id.count()).from(a).where(conditions(filter)).fetchOne();
        return total == null ? 0 : total;
    }

    /** 최근 요청부터(ID 내림차순). */
    public List<AdminActionReceipt> find(Filter filter, int size, long offset) {
        return query.selectFrom(a)
                .where(conditions(filter))
                .orderBy(a.id.desc())
                .limit(size)
                .offset(offset)
                .fetch()
                .stream()
                .map(AdminActionReceipt::from)
                .toList();
    }

    private static BooleanExpression[] conditions(Filter filter) {
        AdminAction action = filter.action();
        return new BooleanExpression[] {
            action == null ? null : a.action.eq(action),
            // 대상 번호는 대상 종류와 같이 걸어 다른 테이블의 같은 숫자 ID와 섞이지 않게 한다.
            filter.targetId() == null || action == null
                    ? null
                    : a.targetType.eq(action.targetType()).and(a.targetId.eq(filter.targetId())),
            filter.orderId() == null ? null : a.orderId.eq(filter.orderId()),
        };
    }
}
