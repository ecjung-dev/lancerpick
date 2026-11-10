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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * K3 관리자 운영 현황. 실제 Security 필터·MySQL로 권한(현재 DB 역할)·no-store와, 문의·알림 건수가 실제 행 변화를 따라가는지
 * 본다. 공유 테스트 DB에 다른 테스트 행이 있을 수 있어 절대값 대신 "추가 전후 차이"를 비교한다.
 * 타이머 24시간 경계·충전 UNKNOWN 조건은 AdminDashboardServiceTests가 본다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class AdminDashboardApiIntegrationTests {
  @Autowired MemberRepository members;
  @Autowired OutboxEventRepository events;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired JsonMapper mapper;

  long admin, user;
  final List<Long> memberIds = new ArrayList<>();
  final List<Long> eventIds = new ArrayList<>();
  final List<Long> inquiryIds = new ArrayList<>();
  LocalDateTime at;

  record Principal(Long memberId) implements AuthenticatedMember {}

  static UsernamePasswordAuthenticationToken auth(long memberId, String role) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new Principal(memberId), null, AuthorityUtils.createAuthorityList("ROLE_" + role));
  }

  @BeforeEach
  void fixture() {
    at = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5).truncatedTo(ChronoUnit.MICROS);
    admin = member();
    user = member();
    jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", admin);
  }

  long member() {
    long id = members.saveAndFlush(Member.create(UUID.randomUUID() + "@admin-dashboard.test", "fixture", "표시 이름", at)).getId();
    memberIds.add(id);
    return id;
  }

  @AfterEach
  void cleanup() {
    for (long id : inquiryIds) jdbc.update("DELETE FROM support_inquiries WHERE id=?", id);
    for (long id : eventIds) jdbc.update("DELETE FROM outbox_events WHERE id=?", id);
    for (long id : memberIds) jdbc.update("DELETE FROM members WHERE id=?", id);
  }

  JsonNode summary() throws Exception {
    var body = mvc.perform(get("/api/admin/dashboard").with(authentication(auth(admin, "ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.checkedAt").isNotEmpty())
        .andReturn().getResponse().getContentAsString();
    return mapper.readTree(body);
  }

  void generalInquiry(String status) {
    // GENERAL 문의는 주문·취소 요청 없이 둘 수 있다(ck_inquiry_type). CLOSED만 resolved_at이 필요하다.
    // 연결 풀에서 다른 연결이 쓰일 수 있어 LAST_INSERT_ID 대신 고유 제목으로 ID를 찾는다.
    String subject = "dashboard-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO support_inquiries(requester_id,order_id,cancellation_request_id,type,status,subject,resolved_at,created_at,updated_at)"
            + " VALUES (?,NULL,NULL,'GENERAL',?,?,?,?,?)",
        user, status, subject, "CLOSED".equals(status) ? at : null, at, at);
    inquiryIds.add(jdbc.queryForObject("SELECT id FROM support_inquiries WHERE subject=?", Long.class, subject));
  }

  void outbox(int attempts, boolean complete) {
    var event = OutboxEvent.pending("ORDER_TEST", 1, "ORDER", 8_900_000_000_000_000L + eventIds.size(), "{}", at);
    for (int i = 0; i < attempts; i++) event.fail(OutboxErrorCode.PROCESSING_FAILED, at, 5, 30);
    if (complete) event.complete(at);
    eventIds.add(events.saveAndFlush(event).getId());
  }

  @Test
  void countsFollowActualRowsAndQueryChangesNothing() throws Exception {
    var before = summary();
    generalInquiry("OPEN");
    generalInquiry("OPEN");
    generalInquiry("IN_REVIEW");
    generalInquiry("CLOSED");
    outbox(5, false); // FAILED
    outbox(2, false); // RETRYING
    outbox(0, false); // 처음 대기: 세지 않음
    outbox(0, true); // 완료: 세지 않음
    String rows = jdbc.queryForList("SELECT id,status,attempt_count FROM outbox_events WHERE id IN (" +
        String.join(",", eventIds.stream().map(String::valueOf).toList()) + ") ORDER BY id").toString();

    var after = summary();
    assertThat(after.at("/inquiries/open").asLong() - before.at("/inquiries/open").asLong()).isEqualTo(2);
    assertThat(after.at("/inquiries/inReview").asLong() - before.at("/inquiries/inReview").asLong()).isEqualTo(1);
    assertThat(after.at("/notifications/failed").asLong() - before.at("/notifications/failed").asLong()).isEqualTo(1);
    assertThat(after.at("/notifications/retrying").asLong() - before.at("/notifications/retrying").asLong()).isEqualTo(1);
    // 조회는 상태를 바꾸지 않는다.
    assertThat(jdbc.queryForList("SELECT id,status,attempt_count FROM outbox_events WHERE id IN (" +
        String.join(",", eventIds.stream().map(String::valueOf).toList()) + ") ORDER BY id").toString()).isEqualTo(rows);
  }

  @Test
  void onlyCurrentAdminsCanRead() throws Exception {
    mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/admin/dashboard").with(authentication(auth(user, "USER")))).andExpect(status().isForbidden());
    // 세션은 ADMIN이지만 DB에서 일반 회원인 경우.
    mvc.perform(get("/api/admin/dashboard").with(authentication(auth(user, "ADMIN"))))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }
}
