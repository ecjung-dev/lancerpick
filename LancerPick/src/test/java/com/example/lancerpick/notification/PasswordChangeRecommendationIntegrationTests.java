package com.example.lancerpick.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.member.dto.ChangePasswordRequest;
import com.example.lancerpick.member.service.MemberChangePolicy;
import com.example.lancerpick.member.service.MemberService;
import com.example.lancerpick.notification.entity.OutboxEvent;
import com.example.lancerpick.notification.repository.NotificationRepository;
import com.example.lancerpick.notification.repository.OutboxEventRepository;
import com.example.lancerpick.notification.service.OutboxDispatcher;
import com.example.lancerpick.notification.service.PasswordChangeRecommendations;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.support.TestMembers;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 비밀번호 변경 권고(U6): 로그인 뒤 회차별 1건 저장, 기준일 재확인, 로그인 실패 격리, MEMBER 집계 디스패치를 실제 DB로 검사한다.
 * 컨텍스트를 늘리지 않도록 {@link NotificationIntegrationTests}와 같은 시계·스파이 구성을 쓴다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(NotificationIntegrationTests.ClockConfig.class)
class PasswordChangeRecommendationIntegrationTests {
  private static final LocalDateTime BASIS = LocalDateTime.parse("2026-08-31T03:00:00.123456");
  private static final String PASSWORD = "before1234";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired MemberService memberService;
  @Autowired PasswordChangeRecommendations recommendations;
  @Autowired OutboxDispatcher dispatcher;
  @Autowired NotificationIntegrationTests.MutableClock clock;
  @MockitoSpyBean OutboxEventRepository events;
  @MockitoSpyBean NotificationRepository notifications;

  long memberId;
  String email;

  record Principal(Long memberId) implements AuthenticatedMember {}

  @BeforeEach
  void fixture() {
    clock.set(BASIS.toInstant(ZoneOffset.UTC));
    email = "pw-recommend-" + UUID.randomUUID() + "@example.com";
    var member = memberService.signup(TestMembers.signupRequest(jdbc, email, PASSWORD, "권고 대상"));
    memberId = Long.parseLong(member.id());
    assertThat(jdbc.queryForObject(
            "SELECT password_recommendation_base_at FROM members WHERE id=?", LocalDateTime.class, memberId))
        .isEqualTo(BASIS);
  }

  @AfterEach
  void cleanup() {
    reset(events, notifications);
    jdbc.update("DELETE FROM notifications WHERE recipient_id=?", memberId);
    jdbc.update("DELETE FROM outbox_events WHERE aggregate_type='MEMBER' AND aggregate_id=?", memberId);
    jdbc.update("DELETE FROM account_challenges WHERE member_id=?", memberId);
    jdbc.update("DELETE FROM wallet_entries WHERE member_id=?", memberId);
    jdbc.update("DELETE FROM wallets WHERE member_id=?", memberId);
    jdbc.update("DELETE FROM members WHERE id=?", memberId);
    TestMembers.deleteSignupProofs(jdbc, email);
  }

  @Test
  void firstRoundStartsAtExactBoundaryAndIsStoredOnce() {
    // 8/31 기준 6개월 뒤는 한국 달력 2/28(월말 보정).
    var boundary = MemberChangePolicy.sixMonthsAfter(BASIS);
    assertThat(boundary).isEqualTo(LocalDateTime.parse("2027-02-28T03:00:00.123456"));
    at(boundary.minusNanos(1_000));
    assertThat(recommendations.issueIfDue(memberId)).isFalse();
    assertThat(eventCount()).isZero();

    at(boundary);
    assertThat(recommendations.issueIfDue(memberId)).isTrue();
    assertThat(recommendations.issueIfDue(memberId)).isFalse();
    assertThat(eventCount()).isEqualTo(1);
    assertThat(jdbc.queryForObject(
            "SELECT JSON_UNQUOTE(JSON_EXTRACT(payload,'$.round')) FROM outbox_events"
                + " WHERE aggregate_type='MEMBER' AND aggregate_id=?",
            String.class, memberId))
        .isEqualTo("1");
  }

  @Test
  void roundsAreCountedFromTheBasisAndLongAbsenceCreatesOnlyTheCurrentRound() {
    // 2회차는 2/28 + 6개월(8/28)이 아니라 기준에서 12개월(8/31)이다.
    at(LocalDateTime.parse("2027-08-30T03:00:00"));
    assertThat(recommendations.issueIfDue(memberId)).isTrue();
    assertThat(rounds()).containsExactly("1");

    at(MemberChangePolicy.sixMonthPeriodsAfter(BASIS, 2));
    assertThat(recommendations.issueIfDue(memberId)).isTrue();
    assertThat(rounds()).containsExactly("1", "2");

    // 1년 넘게 로그인하지 않으면 지나간 회차를 채우지 않고 현재 회차만 만든다.
    at(MemberChangePolicy.sixMonthPeriodsAfter(BASIS, 5).plusDays(3));
    assertThat(recommendations.issueIfDue(memberId)).isTrue();
    assertThat(recommendations.issueIfDue(memberId)).isFalse();
    assertThat(rounds()).containsExactly("1", "2", "5");
  }

  @Test
  void passwordChangeMovesTheBasisSoTheOldScheduleNoLongerApplies() {
    var due = MemberChangePolicy.sixMonthsAfter(BASIS).plusDays(1);
    at(due);
    memberService.changePassword(memberId, null, new ChangePasswordRequest(PASSWORD, "after5678"));
    assertThat(recommendations.issueIfDue(memberId)).isFalse();
    assertThat(eventCount()).isZero();

    at(MemberChangePolicy.sixMonthsAfter(due));
    assertThat(recommendations.issueIfDue(memberId)).isTrue();
  }

  @Test
  void loginIssuesRecommendationAndDispatchCreatesMemberNotificationWithoutOrder() throws Exception {
    at(MemberChangePolicy.sixMonthsAfter(BASIS));
    login().andExpect(status().isOk());
    login().andExpect(status().isOk());
    assertThat(eventCount()).isEqualTo(1);

    long eventId = memberEventIds().getFirst();
    assertThat(dispatcher.dispatch(eventId)).isTrue();
    assertThat(jdbc.queryForObject("SELECT status FROM outbox_events WHERE id=?", String.class, eventId))
        .isEqualTo(OutboxEvent.COMPLETED);
    mvc.perform(get("/api/notifications").with(authentication(auth(memberId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].type").value(PasswordChangeRecommendations.PASSWORD_CHANGE_RECOMMENDED))
        .andExpect(jsonPath("$.items[0].orderId").doesNotExist())
        .andExpect(jsonPath("$.items[0].productTitle").doesNotExist());
  }

  @Test
  void recommendationFailureDoesNotFailLogin() throws Exception {
    at(MemberChangePolicy.sixMonthsAfter(BASIS));
    doThrow(new DataIntegrityViolationException("forced")).when(events).saveAndFlush(any(OutboxEvent.class));
    login().andExpect(status().isOk()).andExpect(jsonPath("$.id").value(String.valueOf(memberId)));
    assertThat(eventCount()).isZero();

    reset(events);
    login().andExpect(status().isOk());
    assertThat(eventCount()).isEqualTo(1);
  }

  @Test
  void concurrentLoginsStoreOneEventPerRound() throws Exception {
    at(MemberChangePolicy.sixMonthsAfter(BASIS));
    var ready = new CountDownLatch(4);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(4)) {
      var futures = java.util.stream.IntStream.range(0, 4).mapToObj(i -> pool.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
        recommendations.issueAfterLogin(memberId);
        return null;
      })).toList();
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      for (var future : futures) future.get(30, TimeUnit.SECONDS);
    }
    assertThat(eventCount()).isEqualTo(1);
  }

  @Test
  void memberEventForAnotherRecipientFailsWithoutNotifying() {
    long id = insertMemberEvent(PasswordChangeRecommendations.PASSWORD_CHANGE_RECOMMENDED, memberId,
        "{\"recipientIds\":[\"" + (memberId + 1_000_000) + "\"]}");
    assertThat(dispatcher.dispatch(id)).isFalse();
    assertFailed(id, "INVALID_RECIPIENT");

    long unsupported = insertMemberEvent("MEMBER_SOMETHING_ELSE", memberId,
        "{\"recipientIds\":[\"" + memberId + "\"]}");
    assertThat(dispatcher.dispatch(unsupported)).isFalse();
    assertFailed(unsupported, "UNSUPPORTED_EVENT");
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM notifications WHERE recipient_id=?", Long.class, memberId))
        .isZero();
  }

  @Test
  void memberEventForMissingMemberFailsImmediately() {
    long missing = jdbc.queryForObject("SELECT COALESCE(MAX(id),0)+1000000 FROM members", Long.class);
    long id = insertMemberEvent(PasswordChangeRecommendations.PASSWORD_CHANGE_RECOMMENDED, missing,
        "{\"recipientIds\":[\"" + missing + "\"]}");
    try {
      assertThat(dispatcher.dispatch(id)).isFalse();
      assertFailed(id, "AGGREGATE_NOT_FOUND");
    } finally {
      jdbc.update("DELETE FROM outbox_events WHERE id=?", id);
    }
  }

  private org.springframework.test.web.servlet.ResultActions login() throws Exception {
    return mvc.perform(post("/api/auth/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"));
  }

  private void at(LocalDateTime utc) {
    clock.set(utc.toInstant(ZoneOffset.UTC));
  }

  private long insertMemberEvent(String type, long aggregateId, String payload) {
    var at = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    events.saveAndFlush(OutboxEvent.pending(type, 1, "MEMBER", aggregateId, payload, at));
    return jdbc.queryForObject(
        "SELECT MAX(id) FROM outbox_events WHERE aggregate_type='MEMBER' AND aggregate_id=?",
        Long.class, aggregateId);
  }

  private void assertFailed(long id, String code) {
    assertThat(jdbc.queryForObject("SELECT status FROM outbox_events WHERE id=?", String.class, id))
        .isEqualTo(OutboxEvent.FAILED);
    assertThat(jdbc.queryForObject("SELECT last_error_code FROM outbox_events WHERE id=?", String.class, id))
        .isEqualTo(code);
  }

  private long eventCount() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM outbox_events WHERE aggregate_type='MEMBER' AND aggregate_id=?",
        Long.class, memberId);
  }

  private List<Long> memberEventIds() {
    return jdbc.queryForList(
        "SELECT id FROM outbox_events WHERE aggregate_type='MEMBER' AND aggregate_id=? ORDER BY id",
        Long.class, memberId);
  }

  private List<String> rounds() {
    return jdbc.queryForList(
        "SELECT JSON_UNQUOTE(JSON_EXTRACT(payload,'$.round')) FROM outbox_events"
            + " WHERE aggregate_type='MEMBER' AND aggregate_id=? ORDER BY id",
        String.class, memberId);
  }

  private static UsernamePasswordAuthenticationToken auth(long member) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new Principal(member), null, AuthorityUtils.createAuthorityList("ROLE_USER"));
  }
}
