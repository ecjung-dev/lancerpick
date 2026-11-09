package com.example.lancerpick.inquiry.repository;

import com.example.lancerpick.inquiry.entity.QSupportInquiry;
import com.example.lancerpick.inquiry.entity.SupportInquiry;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 문의 신청자 쪽 조회(QueryDSL). 저장·잠금은 {@link SupportInquiryRepository}가 맡는다. */
@Repository
@Profile("!demo")
public class SupportInquiryQueries {
  private static final QSupportInquiry i = QSupportInquiry.supportInquiry;
  private final JPAQueryFactory query;

  public SupportInquiryQueries(EntityManager em) {
    this.query = new JPAQueryFactory(em);
  }

  /**
   * 같은 취소 요청에 열린(OPEN·IN_REVIEW) 취소 검토 문의. DB UNIQUE가 없으므로 호출자는 주문 배타 잠금을 잡은 뒤 이 조회와 저장을 한
   * 트랜잭션에서 수행해야 중복이 생기지 않는다.
   */
  public Optional<SupportInquiry> findOpenCancellationReview(long cancellationRequestId) {
    return Optional.ofNullable(
        query
            .selectFrom(i)
            .where(
                i.type.eq(SupportInquiry.CANCELLATION_REVIEW),
                i.cancellationRequestId.eq(cancellationRequestId),
                i.status.in(SupportInquiry.OPEN, SupportInquiry.IN_REVIEW))
            .orderBy(i.id.asc())
            .fetchFirst());
  }

  public long countMine(long requesterId, Long orderId) {
    Long total = query.select(i.id.count()).from(i).where(mine(requesterId, orderId)).fetchOne();
    return total == null ? 0 : total;
  }

  public List<SupportInquiry> findMine(long requesterId, Long orderId, int size, long offset) {
    return query
        .selectFrom(i)
        .where(mine(requesterId, orderId))
        .orderBy(i.createdAt.desc(), i.id.desc())
        .limit(size)
        .offset(offset)
        .fetch();
  }

  private static BooleanBuilder mine(long requesterId, Long orderId) {
    var where = new BooleanBuilder(i.requesterId.eq(requesterId));
    if (orderId != null) where.and(i.orderId.eq(orderId));
    return where;
  }
}
