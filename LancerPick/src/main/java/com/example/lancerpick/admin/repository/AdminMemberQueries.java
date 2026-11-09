package com.example.lancerpick.admin.repository;

import com.example.lancerpick.admin.dto.AdminMemberResponses.Sanction;
import com.example.lancerpick.admin.dto.AdminMemberResponses.Summary;
import com.example.lancerpick.member.entity.QMember;
import com.example.lancerpick.member.entity.QMemberSanction;
import com.example.lancerpick.member.entity.SanctionKind;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!demo")
public class AdminMemberQueries {
  private static final QMember M = QMember.member;
  private static final QMemberSanction S = QMemberSanction.memberSanction;
  private final JPAQueryFactory query;

  public record Filter(String keyword, boolean numeric, Long memberId, SanctionKind restriction) {}

  public record MemberDetail(Summary member, LocalDateTime emailVerifiedAt) {}

  public AdminMemberQueries(EntityManager entityManager) {
    query = new JPAQueryFactory(entityManager);
  }

  private static BooleanExpression active(LocalDateTime now) {
    return S.revokedAt.isNull().and(S.startsAt.loe(now)).and(S.endsAt.gt(now));
  }

  private static BooleanExpression restricted(SanctionKind kind, LocalDateTime now) {
    return JPAExpressions.selectOne()
        .from(S)
        .where(S.memberId.eq(M.id), S.kind.eq(kind), active(now))
        .exists();
  }

  private static BooleanBuilder conditions(Filter filter, LocalDateTime now) {
    var result = new BooleanBuilder();
    if (filter.numeric()) {
      result.and(filter.memberId() == null ? M.id.isNull() : M.id.eq(filter.memberId()));
    } else if (filter.keyword() != null) {
      String literal =
          "%" + filter.keyword().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
      result.and(M.email.like(literal, '!').or(M.displayName.like(literal, '!')));
    }
    var security = restricted(SanctionKind.SECURITY_LOCK, now);
    var general = restricted(SanctionKind.GENERAL_RESTRICTION, now);
    if (filter.restriction() == SanctionKind.SECURITY_LOCK) result.and(security);
    else if (filter.restriction() == SanctionKind.GENERAL_RESTRICTION)
      result.and(general.and(security.not()));
    else if (filter.restriction() == SanctionKind.NONE)
      result.and(security.not().and(general.not()));
    return result;
  }

  private static com.querydsl.core.types.dsl.StringExpression restriction(LocalDateTime now) {
    return new CaseBuilder()
        .when(restricted(SanctionKind.SECURITY_LOCK, now))
        .then("SECURITY_LOCK")
        .when(restricted(SanctionKind.GENERAL_RESTRICTION, now))
        .then("GENERAL_RESTRICTION")
        .otherwise("NONE");
  }

  public long count(Filter filter, LocalDateTime now) {
    return query.select(M.id.count()).from(M).where(conditions(filter, now)).fetchOne();
  }

  public List<Summary> list(Filter filter, LocalDateTime now, long offset, int size) {
    var status = restriction(now);
    return query
        .select(M.id, M.email, M.displayName, M.role, M.createdAt, status)
        .from(M)
        .where(conditions(filter, now))
        .orderBy(M.createdAt.desc(), M.id.desc())
        .offset(offset)
        .limit(size)
        .fetch()
        .stream()
        .map(row ->
            new Summary(
                row.get(M.id).toString(),
                row.get(M.email),
                row.get(M.displayName),
                row.get(M.role).name(),
                row.get(status),
                at(row.get(M.createdAt))))
        .toList();
  }

  public Optional<MemberDetail> detail(long memberId, LocalDateTime now) {
    var status = restriction(now);
    var row =
        query
            .select(M.id, M.email, M.displayName, M.role, M.createdAt, M.emailVerifiedAt, status)
            .from(M)
            .where(M.id.eq(memberId))
            .fetchOne();
    if (row == null) return Optional.empty();
    return Optional.of(
        new MemberDetail(
            new Summary(
                row.get(M.id).toString(),
                row.get(M.email),
                row.get(M.displayName),
                row.get(M.role).name(),
                row.get(status),
                at(row.get(M.createdAt))),
            row.get(M.emailVerifiedAt)));
  }

  public List<Sanction> activeSanctions(long memberId, LocalDateTime now) {
    var result = new java.util.ArrayList<Sanction>();
    for (var kind : List.of(SanctionKind.SECURITY_LOCK, SanctionKind.GENERAL_RESTRICTION)) {
      var row =
          query
              .select(
                  S.id,
                  S.kind,
                  S.reason,
                  S.appliedBy,
                  S.startsAt,
                  S.endsAt,
                  S.revokedAt,
                  S.revokedBy,
                  S.revokeReason,
                  S.timerStopDeliveredAt)
              .from(S)
              .where(S.memberId.eq(memberId), S.kind.eq(kind), active(now))
              .orderBy(S.id.desc())
              .fetchFirst();
      if (row != null) result.add(sanction(row, now));
    }
    return List.copyOf(result);
  }

  public long historyCount(long memberId) {
    return query.select(S.id.count()).from(S).where(S.memberId.eq(memberId)).fetchOne();
  }

  public List<Sanction> history(long memberId, LocalDateTime now, long offset, int size) {
    return query
        .select(
            S.id,
            S.kind,
            S.reason,
            S.appliedBy,
            S.startsAt,
            S.endsAt,
            S.revokedAt,
            S.revokedBy,
            S.revokeReason,
            S.timerStopDeliveredAt)
        .from(S)
        .where(S.memberId.eq(memberId))
        .orderBy(S.startsAt.desc(), S.id.desc())
        .offset(offset)
        .limit(size)
        .fetch()
        .stream()
        .map(row -> sanction(row, now))
        .toList();
  }

  private static Sanction sanction(com.querydsl.core.Tuple row, LocalDateTime now) {
    var revoked = row.get(S.revokedAt);
    var starts = row.get(S.startsAt);
    var ends = row.get(S.endsAt);
    String state =
        revoked != null
            ? "REVOKED"
            : !ends.isAfter(now) ? "EXPIRED" : starts.isAfter(now) ? "SCHEDULED" : "ACTIVE";
    return new Sanction(
        row.get(S.id).toString(),
        row.get(S.kind).name(),
        state,
        row.get(S.reason),
        row.get(S.appliedBy).toString(),
        at(starts),
        at(ends),
        at(revoked),
        row.get(S.revokedBy) == null ? null : row.get(S.revokedBy).toString(),
        row.get(S.revokeReason),
        at(row.get(S.timerStopDeliveredAt)));
  }

  private static OffsetDateTime at(LocalDateTime value) {
    return value == null ? null : value.atOffset(ZoneOffset.UTC);
  }
}
