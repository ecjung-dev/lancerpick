package com.example.lancerpick.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.lancerpick.admin.repository.AdminFinanceQueries;
import com.example.lancerpick.admin.repository.AdminInquiryQueries;
import com.example.lancerpick.admin.repository.AdminInquiryQueries.InquiryFilter;
import com.example.lancerpick.admin.repository.AdminOutboxQueries;
import com.example.lancerpick.admin.repository.AdminOutboxQueries.Issue;
import com.example.lancerpick.admin.repository.AdminReviewTimerQueries;
import com.example.lancerpick.admin.service.AdminDashboardService;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.entity.MemberRole;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.wallet.entity.WalletTopupStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** K3 운영 현황 서비스 단위 검사: 어떤 조건으로 세는지(타이머 24시간 경계·충전 UNKNOWN·문의 상태·알림 구분). DB 없음. */
class AdminDashboardServiceTests {
  final MemberRepository members = mock(MemberRepository.class);
  final AdminInquiryQueries inquiries = mock(AdminInquiryQueries.class);
  final AdminReviewTimerQueries timers = mock(AdminReviewTimerQueries.class);
  final AdminOutboxQueries outbox = mock(AdminOutboxQueries.class);
  final AdminFinanceQueries finance = mock(AdminFinanceQueries.class);
  final Clock clock = Clock.fixed(Instant.parse("2026-11-09T03:00:00.123456789Z"), ZoneOffset.UTC);
  final AdminDashboardService service = new AdminDashboardService(members, inquiries, timers, outbox, finance, clock);

  @Test
  void eachCountUsesTheDocumentedCondition() {
    var admin = mock(Member.class);
    when(admin.getRole()).thenReturn(MemberRole.ADMIN);
    when(members.findById(1L)).thenReturn(Optional.of(admin));
    when(inquiries.count(new InquiryFilter("OPEN", null))).thenReturn(3L);
    when(inquiries.count(new InquiryFilter("IN_REVIEW", null))).thenReturn(1L);
    when(timers.countActive()).thenReturn(7L);
    // 마이크로초로 자른 현재 시각 + 24시간이 경계다.
    when(timers.countActiveExpiringBefore(LocalDateTime.of(2026, 11, 10, 3, 0, 0, 123_456_000))).thenReturn(2L);
    when(outbox.count(Issue.FAILED, null)).thenReturn(4L);
    when(outbox.count(Issue.RETRYING, null)).thenReturn(5L);
    when(finance.countTopups(new AdminFinanceQueries.TopupFilter(null, WalletTopupStatus.UNKNOWN))).thenReturn(6L);

    var result = service.summary(1L);

    assertThat(result.inquiries().open()).isEqualTo(3);
    assertThat(result.inquiries().inReview()).isEqualTo(1);
    assertThat(result.reviewTimers().active()).isEqualTo(7);
    assertThat(result.reviewTimers().expiringWithin24Hours()).isEqualTo(2);
    assertThat(result.notifications().failed()).isEqualTo(4);
    assertThat(result.notifications().retrying()).isEqualTo(5);
    assertThat(result.topups().unknown()).isEqualTo(6);
    assertThat(result.checkedAt().toString()).isEqualTo("2026-11-09T03:00:00.123456Z");
  }

  @Test
  void nonAdminsAreRejectedBeforeCounting() {
    var user = mock(Member.class);
    when(user.getRole()).thenReturn(MemberRole.USER);
    when(members.findById(2L)).thenReturn(Optional.of(user));
    when(members.findById(3L)).thenReturn(Optional.empty());
    when(timers.countActiveExpiringBefore(any())).thenThrow(new AssertionError("권한 확인 전에 세면 안 된다"));

    assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> service.summary(2L)))
        .hasMessageContaining("관리자만");
    assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> service.summary(3L)))
        .hasMessageContaining("로그인");
  }
}
