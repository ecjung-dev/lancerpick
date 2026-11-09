package com.example.lancerpick.admin.repository;

import com.example.lancerpick.admin.dto.AdminInquiryResponses.Party;
import com.example.lancerpick.admin.dto.AdminInquiryResponses.Summary;
import com.example.lancerpick.inquiry.entity.QSupportInquiry;
import com.example.lancerpick.member.entity.QMember;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 관리자 운영 문의 목록 조회(QueryDSL, 읽기 전용). 상태 변경은 각 서비스가 JPA 잠금으로 한다. */
@Repository
@Profile("!demo")
public class AdminInquiryQueries {
    private static final QSupportInquiry i = QSupportInquiry.supportInquiry;
    private static final QMember m = QMember.member;
    private final JPAQueryFactory query;

    public AdminInquiryQueries(EntityManager em) {
        this.query = new JPAQueryFactory(em);
    }

    /** status·type이 null이면 조건을 걸지 않는다. 값 검사는 서비스가 한다. */
    public record InquiryFilter(String status, String type) {}

    public long count(InquiryFilter filter) {
        Long total = query.select(i.id.count()).from(i).where(where(filter)).fetchOne();
        return total == null ? 0 : total;
    }

    /** 최신 접수 순. 처리할 문의를 먼저 보려면 status=OPEN으로 거른다. */
    public List<Summary> find(InquiryFilter filter, int size, long offset) {
        return query.select(i, m.displayName)
                .from(i)
                .join(m).on(m.id.eq(i.requesterId))
                .where(where(filter))
                .orderBy(i.createdAt.desc(), i.id.desc())
                .limit(size)
                .offset(offset)
                .fetch()
                .stream()
                .map(row -> {
                    var v = row.get(i);
                    return new Summary(
                            v.getId().toString(),
                            v.getType(),
                            v.getStatus(),
                            v.getSubject(),
                            id(v.getOrderId()),
                            id(v.getCancellationRequestId()),
                            new Party(Long.toString(v.getRequesterId()), row.get(m.displayName)),
                            at(v.getCreatedAt()),
                            at(v.getResolvedAt()));
                })
                .toList();
    }

    private static BooleanBuilder where(InquiryFilter filter) {
        var where = new BooleanBuilder();
        if (filter.status() != null) where.and(i.status.eq(filter.status()));
        if (filter.type() != null) where.and(i.type.eq(filter.type()));
        return where;
    }

    private static String id(Long value) {
        return value == null ? null : value.toString();
    }

    private static OffsetDateTime at(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
