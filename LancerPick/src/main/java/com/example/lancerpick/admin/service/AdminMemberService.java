package com.example.lancerpick.admin.service;

import com.example.lancerpick.admin.dto.AdminMemberResponses.Detail;
import com.example.lancerpick.admin.dto.AdminMemberResponses.Sanction;
import com.example.lancerpick.admin.dto.AdminMemberResponses.Summary;
import com.example.lancerpick.admin.repository.AdminMemberQueries;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.member.entity.SanctionKind;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 조회는 고정 시각·같은 읽기 스냅샷, 명령은 기존 제재 서비스의 회원 잠금과 별도 트랜잭션을 사용한다. */
@Service
@Profile("!demo")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AdminMemberService {
  private final AdminMemberQueries queries;
  private final AdminAuthorization authorization;
  private final Clock clock;

  public AdminMemberService(
      AdminMemberQueries queries, AdminAuthorization authorization, Clock clock) {
    this.queries = queries;
    this.authorization = authorization;
    this.clock = clock;
  }

  public PageResponse<Summary> list(
      long adminId, String q, String restriction, int page, int size) {
    authorization.requireAdmin(adminId);
    page(page, size);
    String keyword = q == null ? null : q.strip();
    if (keyword != null && keyword.isEmpty()) keyword = null;
    if (keyword != null && keyword.codePointCount(0, keyword.length()) > 100) throw invalid();
    SanctionKind filter = null;
    if (restriction != null) {
      try {
        filter = SanctionKind.valueOf(restriction);
      } catch (IllegalArgumentException failure) {
        throw invalid();
      }
    }
    boolean numeric = keyword != null && keyword.matches("[0-9]+");
    Long memberId = null;
    if (numeric) {
      try {
        long value = Long.parseLong(keyword);
        if (value > 0) memberId = value;
      } catch (NumberFormatException ignored) {
        /* 숫자 범위 밖은 빈 결과다. */
      }
    }
    var condition =
        new AdminMemberQueries.Filter(numeric ? null : keyword, numeric, memberId, filter);
    var now = UtcTimes.nowMicros(clock);
    long count = queries.count(condition, now), offset = (long) page * size;
    return new PageResponse<>(
        offset >= count ? List.of() : queries.list(condition, now, offset, size),
        page,
        size,
        count,
        PageResponse.totalPages(count, size));
  }

  public Detail detail(long adminId, long memberId) {
    authorization.requireAdmin(adminId);
    if (memberId < 1) throw invalid();
    var now = UtcTimes.nowMicros(clock);
    var row = queries.detail(memberId, now).orElseThrow(AdminMemberService::missing);
    return new Detail(
        row.member(),
        row.emailVerifiedAt() == null ? null : row.emailVerifiedAt().atOffset(ZoneOffset.UTC),
        now.atOffset(ZoneOffset.UTC),
        adminId != memberId && !row.member().role().equals("ADMIN"),
        queries.activeSanctions(memberId, now));
  }

  public PageResponse<Sanction> history(long adminId, long memberId, int page, int size) {
    authorization.requireAdmin(adminId);
    page(page, size);
    var now = UtcTimes.nowMicros(clock);
    if (memberId < 1) throw invalid();
    if (queries.detail(memberId, now).isEmpty()) throw missing();
    long count = queries.historyCount(memberId), offset = (long) page * size;
    return new PageResponse<>(
        offset >= count ? List.of() : queries.history(memberId, now, offset, size),
        page,
        size,
        count,
        PageResponse.totalPages(count, size));
  }

  private static void page(int page, int size) {
    if (!PageResponse.isValidRequest(page, size)) throw invalid();
  }

  private static BusinessException missing() {
    return new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "회원을 찾을 수 없습니다.");
  }

  private static BusinessException invalid() {
    return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "회원 조회 조건을 확인해 주세요.");
  }
}
