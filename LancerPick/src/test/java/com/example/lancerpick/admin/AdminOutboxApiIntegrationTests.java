package com.example.lancerpick.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.notification.entity.OutboxErrorCode;
import com.example.lancerpick.notification.entity.OutboxEvent;
import com.example.lancerpick.notification.repository.OutboxEventRepository;
import com.example.lancerpick.support.IntegrationTest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * K6 관리자 알림 처리 실패 조회. 실제 Security 필터·MySQL로 권한(현재 DB 역할)·조건(FAILED/RETRYING/주문 번호)·
 * no-store·페이로드 비노출과 조회가 이벤트 상태를 바꾸지 않음을 본다. 테스트 프로필은 처리 스케줄러가 꺼져 있다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class AdminOutboxApiIntegrationTests {
  @Autowired MemberRepository members;
  @Autowired OutboxEventRepository events;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;

  long admin, user, orderId, otherOrderId;
  long failed, retrying, waiting, completed, otherTypeFailed;
  final List<Long> memberIds = new ArrayList<>();
  final List<Long> eventIds = new ArrayList<>();

  record Principal(Long memberId) implements AuthenticatedMember {}

  static UsernamePasswordAuthenticationToken auth(long memberId, String role) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new Principal(memberId), null, AuthorityUtils.createAuthorityList("ROLE_" + role));
  }

  @BeforeEach
  void fixture() {
    var at = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5).truncatedTo(ChronoUnit.MICROS);
    admin = member(at);
    user = member(at);
    jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", admin);
    // 다른 테스트가 남긴 이벤트와 섞이지 않도록 존재하지 않는 큰 주문 번호를 쓴다(aggregate_id에는 FK가 없다).
    orderId = ThreadLocalRandom.current().nextLong(8_000_000_000_000_000L, 9_000_000_000_000_000L);
    otherOrderId = orderId + 1;
    failed = event("ORDER", orderId, at, 5, OutboxErrorCode.INVALID_RECIPIENT, false);
    retrying = event("ORDER", orderId, at.plusSeconds(1), 2, OutboxErrorCode.LOCK_UNAVAILABLE, false);
    waiting = event("ORDER", orderId, at.plusSeconds(2), 0, null, false);
    completed = event("ORDER", orderId, at.plusSeconds(3), 0, null, true);
    // 같은 숫자 ID라도 집계 유형이 다르면 주문 번호 조건에 포함하지 않는다.
    otherTypeFailed = event("MEMBER", orderId, at.plusSeconds(4), 5, OutboxErrorCode.PROCESSING_FAILED, false);
    event("ORDER", otherOrderId, at.plusSeconds(5), 5, OutboxErrorCode.AGGREGATE_NOT_FOUND, false);
  }

  long member(LocalDateTime at) {
    long id = members.saveAndFlush(Member.create(UUID.randomUUID() + "@admin-outbox.test", "fixture", "표시 이름", at)).getId();
    memberIds.add(id);
    return id;
  }

  /** attempts회 실패를 기록한다(최대 5회면 FAILED). complete=true면 처리 완료 상태로 둔다. */
  long event(String aggregateType, long aggregateId, LocalDateTime at, int attempts, OutboxErrorCode code, boolean complete) {
    var event = OutboxEvent.pending("ORDER_TEST", 1, aggregateType, aggregateId, "{\"recipientId\":\"secret-recipient\"}", at);
    for (int i = 0; i < attempts; i++) event.fail(code, at, 5, 30);
    if (complete) event.complete(at);
    long id = events.saveAndFlush(event).getId();
    eventIds.add(id);
    return id;
  }

  @AfterEach
  void cleanup() {
    for (long id : eventIds) jdbc.update("DELETE FROM outbox_events WHERE id=?", id);
    for (long id : memberIds) jdbc.update("DELETE FROM members WHERE id=?", id);
  }

  String snapshot() {
    return jdbc.queryForList(
            "SELECT id,status,attempt_count,next_attempt_at,last_error_code,processed_at FROM outbox_events WHERE aggregate_id IN (?,?) ORDER BY id",
            orderId, otherOrderId)
        .toString();
  }

  @Test
  void failedIsDefaultAndOrderFilterKeepsOnlyThatOrder() throws Exception {
    String before = snapshot();
    var result = mvc.perform(get("/api/admin/outbox-events").param("orderId", Long.toString(orderId))
            .with(authentication(auth(admin, "ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(Long.toString(failed)))
        .andExpect(jsonPath("$.items[0].aggregateType").value("ORDER"))
        .andExpect(jsonPath("$.items[0].aggregateId").value(Long.toString(orderId)))
        .andExpect(jsonPath("$.items[0].status").value("FAILED"))
        .andExpect(jsonPath("$.items[0].attemptCount").value(5))
        .andExpect(jsonPath("$.items[0].lastErrorCode").value("INVALID_RECIPIENT"))
        .andExpect(jsonPath("$.items[0].nextAttemptAt").doesNotExist())
        .andReturn();
    // 페이로드(수신자 등)는 응답하지 않는다. 조회는 상태를 바꾸지 않는다.
    assertThat(result.getResponse().getContentAsString()).doesNotContain("secret-recipient", "payload");
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void retryingListsPendingEventsThatAlreadyFailedOnce() throws Exception {
    mvc.perform(get("/api/admin/outbox-events").param("status", "RETRYING").param("orderId", Long.toString(orderId))
            .with(authentication(auth(admin, "ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(Long.toString(retrying)))
        .andExpect(jsonPath("$.items[0].status").value("RETRYING"))
        .andExpect(jsonPath("$.items[0].attemptCount").value(2))
        .andExpect(jsonPath("$.items[0].lastErrorCode").value("LOCK_UNAVAILABLE"))
        .andExpect(jsonPath("$.items[0].nextAttemptAt").isNotEmpty());
  }

  @Test
  void withoutOrderFilterAllFailedTypesAppearNewestFirst() throws Exception {
    // 다른 테스트의 FAILED 이벤트가 있을 수 있어 개수 대신 이번 이벤트의 포함·순서만 본다.
    var body = mvc.perform(get("/api/admin/outbox-events").param("size", "100")
            .with(authentication(auth(admin, "ADMIN"))))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    int other = body.indexOf("\"id\":\"" + otherTypeFailed + "\"");
    int mine = body.indexOf("\"id\":\"" + failed + "\"");
    assertThat(other).isNotNegative();
    assertThat(mine).isGreaterThan(other);
    assertThat(body).doesNotContain("\"id\":\"" + waiting + "\"", "\"id\":\"" + completed + "\"", "\"id\":\"" + retrying + "\"");
  }

  @Test
  void onlyCurrentAdminsAndDocumentedValuesAreAccepted() throws Exception {
    mvc.perform(get("/api/admin/outbox-events")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/admin/outbox-events").with(authentication(auth(user, "USER"))))
        .andExpect(status().isForbidden());
    // 세션은 ADMIN이지만 DB에서 강등된 경우.
    mvc.perform(get("/api/admin/outbox-events").with(authentication(auth(user, "ADMIN"))))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    for (String[] bad : List.of(
        new String[] {"status", "failed"}, new String[] {"status", "PENDING"}, new String[] {"orderId", "0"},
        new String[] {"orderId", "abc"}, new String[] {"size", "101"}, new String[] {"page", "-1"})) {
      mvc.perform(get("/api/admin/outbox-events").param(bad[0], bad[1]).with(authentication(auth(admin, "ADMIN"))))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
  }
}
