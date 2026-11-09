package com.example.lancerpick.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.seller.entity.SellerProfile;
import com.example.lancerpick.seller.repository.SellerProfileRepository;
import com.example.lancerpick.support.IntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real committed MySQL changes via HTTP, including authority recheck, expiry and concurrent
 * retries.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(AdminMemberApiIntegrationTests.TimeConfig.class)
class AdminMemberApiIntegrationTests {
  private static final LocalDateTime NOW = LocalDateTime.parse("2026-11-09T06:00:00.123456");
  private static final String PASSWORD = "testPass1234";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper mapper;
  @Autowired MemberRepository members;
  @Autowired SellerProfileRepository profiles;
  @Autowired PasswordEncoder encoder;
  @Autowired AdminClock clock;
  final List<Long> created = new ArrayList<>();
  String prefix, targetEmail;
  long admin, anotherAdmin, target, other, category;

  record Principal(Long memberId) implements AuthenticatedMember {}

  @BeforeEach
  void fixture() {
    clock.at(NOW);
    prefix = "admin-member-" + UUID.randomUUID();
    admin = member("관리자", true);
    anotherAdmin = member("다른 관리자", true);
    target = member("대상_%!", false);
    other = member("다른 회원", false);
    targetEmail = members.findById(target).orElseThrow().getEmail();
  }

  @AfterEach
  void cleanup() {
    for (long id : created) jdbc.update("DELETE FROM member_sanctions WHERE member_id=?", id);
    for (long id : created) {
      jdbc.update("DELETE FROM products WHERE seller_id=?", id);
      jdbc.update("DELETE FROM seller_profiles WHERE member_id=?", id);
      jdbc.update("DELETE FROM members WHERE id=?", id);
    }
    if (category != 0) jdbc.update("DELETE FROM categories WHERE id=?", category);
  }

  @Test
  void adminReadsOnlySafeProjectionsWithLiteralSearchNumericIdsAndPaging() throws Exception {
    read("?q=" + prefix + "&size=1", admin, true)
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(jsonPath("$.totalPages").value(4))
        .andExpect(jsonPath("$.items[0].id").value(Long.toString(other)))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password"))));
    read(
            "?q=" + java.net.URLEncoder.encode("_%!", java.nio.charset.StandardCharsets.UTF_8),
            admin,
            true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(Long.toString(target)));
    read("?q=" + target, admin, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    read("?q=9223372036854775808", admin, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
    read("?q=" + prefix + "&page=99", admin, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(jsonPath("$.items").isEmpty());
    read("/" + admin, admin, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canManage").value(false));
    read("/" + target, admin, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canManage").value(true))
        .andExpect(jsonPath("$.member.restriction").value("NONE"));
  }

  @Test
  void authenticationCsrfCurrentDatabaseRoleAndProtectedTargetsAreEnforced() throws Exception {
    mvc.perform(get("/api/admin/members")).andExpect(status().isUnauthorized());
    read("", target, false).andExpect(status().isForbidden());
    read("/" + other, target, true).andExpect(status().isForbidden());
    read("/" + other + "/sanctions", target, true).andExpect(status().isForbidden());
    var body = body("GENERAL_RESTRICTION", UUID.randomUUID());
    mvc.perform(
            post("/api/admin/members/" + target + "/sanctions")
                .with(authentication(auth(admin, true)))
                .contentType("application/json")
                .content(json(body)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    apply(admin, admin, body).andExpect(status().isBadRequest());
    apply(admin, anotherAdmin, body).andExpect(status().isForbidden());
    jdbc.update("UPDATE members SET role='USER' WHERE id=?", admin);
    read("", admin, true).andExpect(status().isForbidden());
    apply(admin, target, body).andExpect(status().isForbidden());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM member_sanctions WHERE member_id=?", Long.class, target))
        .isZero();
  }

  @Test
  void overlappingKindsUseSecurityPriorityAndExpiryIsLogicalWithoutRestoringOldSessions()
      throws Exception {
    long general = applyId(body("GENERAL_RESTRICTION", UUID.randomUUID()));
    var second = body("GENERAL_RESTRICTION", UUID.randomUUID());
    second.put("endsAt", NOW.plusDays(2).atOffset(ZoneOffset.UTC).toString());
    long replacement = applyId(second);
    var security = body("SECURITY_LOCK", UUID.randomUUID());
    security.put("endsAt", NOW.plusHours(1).atOffset(ZoneOffset.UTC).toString());
    applyId(security);
    read("/" + target, admin, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.member.restriction").value("SECURITY_LOCK"))
        .andExpect(jsonPath("$.activeSanctions.length()").value(2));
    read("?q=" + prefix + "&restriction=GENERAL_RESTRICTION", admin, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    read("?q=" + prefix + "&restriction=NONE", admin, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3));
    clock.at(NOW.plusHours(1));
    read("/" + target, admin, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.member.restriction").value("GENERAL_RESTRICTION"));
    clock.at(NOW.plusDays(2));
    read("/" + target, admin, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.member.restriction").value("NONE"))
        .andExpect(jsonPath("$.activeSanctions").isEmpty());
    read("/" + target + "/sanctions?size=1", admin, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.items[0].state").value("EXPIRED"));
    assertThat(
            jdbc.queryForObject(
                "SELECT revoked_at FROM member_sanctions WHERE id=?", LocalDateTime.class, general))
        .isEqualTo(NOW);
    assertThat(
            jdbc.queryForObject(
                "SELECT revoked_at FROM member_sanctions WHERE id=?",
                LocalDateTime.class,
                replacement))
        .isNull();
  }

  @Test
  void applyReplayIsStableAfterExpiryAndForeignRecordCannotBeRevoked() throws Exception {
    var body = body("GENERAL_RESTRICTION", UUID.randomUUID());
    body.put("adminId", Long.toString(anotherAdmin));
    long id = applyId(body);
    clock.at(NOW.plusDays(2));
    apply(admin, target, body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sanctionId").value(Long.toString(id)));
    var changed = new LinkedHashMap<>(body);
    changed.put("reason", "다른 사유");
    apply(admin, target, changed)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SANCTION_REQUEST_CONFLICT"));
    revoke(other, id, "잘못된 대상").andExpect(status().isNotFound());
    revoke(target, id, "처음 해제 사유").andExpect(status().isNoContent());
    revoke(target, id, "두 번째 사유").andExpect(status().isNoContent());
    read("/" + target + "/sanctions", admin, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].appliedBy").value(Long.toString(admin)))
        .andExpect(jsonPath("$.items[0].revokeReason").value("처음 해제 사유"));
  }

  @Test
  void concurrentSecurityLockReplayCreatesOneRowAndInvalidatesAuthenticationOnce()
      throws Exception {
    String body = json(body("SECURITY_LOCK", UUID.randomUUID()));
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var calls =
          java.util.stream.IntStream.range(0, 2)
              .mapToObj(ignored ->
                  pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return mvc.perform(
                            post("/api/admin/members/" + target + "/sanctions")
                                .with(authentication(auth(admin, true)))
                                .with(csrf())
                                .contentType("application/json")
                                .content(body))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
                  }))
              .toList();
      start.countDown();
      assertThat(calls.get(0).get(20, TimeUnit.SECONDS))
          .isEqualTo(calls.get(1).get(20, TimeUnit.SECONDS));
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM member_sanctions WHERE member_id=?", Long.class, target))
        .isEqualTo(1);
    assertThat(members.findById(target).orElseThrow().getAuthVersion()).isEqualTo(1);
  }

  @Test
  void concurrentReplacementAndRevocationDoNotRemoveTheNewSanction() throws Exception {
    long old = applyId(body("GENERAL_RESTRICTION", UUID.randomUUID()));
    var next = body("GENERAL_RESTRICTION", UUID.randomUUID());
    next.put("reason", "새 제재");
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var apply =
          pool.submit(() -> {
            start.await(10, TimeUnit.SECONDS);
            return apply(admin, target, next).andExpect(status().isOk()).andReturn();
          });
      var revoke =
          pool.submit(() -> {
            start.await(10, TimeUnit.SECONDS);
            return revoke(target, old, "기존 건 해제").andExpect(status().isNoContent()).andReturn();
          });
      start.countDown();
      apply.get(20, TimeUnit.SECONDS);
      revoke.get(20, TimeUnit.SECONDS);
    }
    read("/" + target, admin, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeSanctions.length()").value(1))
        .andExpect(jsonPath("$.activeSanctions[0].reason").value("새 제재"));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM member_sanctions WHERE member_id=?", Long.class, target))
        .isEqualTo(2);
  }

  @Test
  void securityLockBlocksRealLoginAndExistingSessionWhileExpiryRequiresFreshLogin()
      throws Exception {
    MockHttpSession session =
        login().andExpect(status().isOk()).andReturn().getRequest().getSession(false)
                instanceof MockHttpSession s
            ? s
            : null;
    assertThat(session).isNotNull();
    applyId(body("SECURITY_LOCK", UUID.randomUUID()));
    mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
    login().andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MEMBER_LOCKED"));
    clock.at(NOW.plusDays(1));
    login().andExpect(status().isOk());
    assertThat(members.findById(target).orElseThrow().getAuthVersion()).isEqualTo(1);
  }

  @Test
  void generalRestrictionKeepsSessionButBlocksNewProductUntilRevoked() throws Exception {
    profiles.saveAndFlush(SellerProfile.create(target, "소개", "전문 분야", null, NOW));
    jdbc.update("INSERT INTO categories(name,display_order) VALUES (?,0)", prefix);
    category = jdbc.queryForObject("SELECT id FROM categories WHERE name=?", Long.class, prefix);
    var session =
        (MockHttpSession)
            login().andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    long id = applyId(body("GENERAL_RESTRICTION", UUID.randomUUID()));
    mvc.perform(get("/api/me").session(session)).andExpect(status().isOk());
    var product =
        Map.of(
            "categoryId",
            Long.toString(category),
            "title",
            "상품",
            "description",
            "설명",
            "scopeDescription",
            "범위",
            "preparationDescription",
            "준비",
            "priceAmount",
            10000,
            "durationDays",
            1);
    mvc.perform(
            post("/api/products")
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content(json(product)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("MEMBER_RESTRICTED"));
    revoke(target, id, "해제").andExpect(status().isNoContent());
    mvc.perform(
            post("/api/products")
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content(json(product)))
        .andExpect(status().isCreated());
    assertThat(members.findById(target).orElseThrow().getAuthVersion()).isZero();
  }

  @Test
  void invalidTypesDatesUuidRangesAndMissingMembersFailWithoutWrites() throws Exception {
    var valid = body("GENERAL_RESTRICTION", UUID.randomUUID());
    for (var field :
        List.of(
            Map.entry("kind", "NONE"),
            Map.entry("kind", "unknown"),
            Map.entry("reason", "  "),
            Map.entry("endsAt", NOW.toString()),
            Map.entry("endsAt", NOW.atOffset(ZoneOffset.UTC).toString()),
            Map.entry("endsAt", "+999999999-12-31T23:59:59-18:00"),
            Map.entry("requestKey", "1-1-1-1-1"))) {
      var bad = new LinkedHashMap<>(valid);
      bad.put(field.getKey(), field.getValue());
      apply(admin, target, bad).andExpect(status().isBadRequest());
    }
    var badType = new LinkedHashMap<>(valid);
    badType.put("reason", true);
    apply(admin, target, badType).andExpect(status().isBadRequest());
    for (String suffix :
        List.of("?page=-1", "?size=101", "?restriction=other", "?q=" + "a".repeat(101), "/0")) {
      read(suffix, admin, true).andExpect(status().isBadRequest());
    }
    read("/" + Long.MAX_VALUE, admin, true).andExpect(status().isNotFound());
    read("/" + Long.MAX_VALUE + "/sanctions", admin, true).andExpect(status().isNotFound());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM member_sanctions WHERE member_id=?", Long.class, target))
        .isZero();
  }

  private Map<String, Object> body(String kind, UUID key) {
    var result = new LinkedHashMap<String, Object>();
    result.put("kind", kind);
    result.put("reason", "정책 위반 확인");
    result.put("endsAt", NOW.plusDays(1).atOffset(ZoneOffset.UTC).toString());
    result.put("requestKey", key.toString());
    return result;
  }

  private long applyId(Map<String, Object> body) throws Exception {
    var response =
        apply(admin, target, body)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return Long.parseLong(mapper.readTree(response).get("sanctionId").asString());
  }

  private ResultActions apply(long actor, long id, Map<String, Object> body) throws Exception {
    return mvc.perform(
        post("/api/admin/members/" + id + "/sanctions")
            .with(authentication(auth(actor, true)))
            .with(csrf())
            .contentType("application/json")
            .content(json(body)));
  }

  private ResultActions revoke(long id, long sanctionId, String reason) throws Exception {
    return mvc.perform(
        post("/api/admin/members/" + id + "/sanctions/" + sanctionId + "/revoke")
            .with(authentication(auth(admin, true)))
            .with(csrf())
            .contentType("application/json")
            .content(json(Map.of("reason", reason))));
  }

  private ResultActions read(String suffix, long id, boolean role) throws Exception {
    return mvc.perform(
        get(java.net.URI.create("/api/admin/members" + suffix))
            .with(authentication(auth(id, role))));
  }

  private ResultActions login() throws Exception {
    return mvc.perform(
        post("/api/auth/login")
            .with(csrf())
            .contentType("application/json")
            .content(json(Map.of("email", targetEmail, "password", PASSWORD))));
  }

  private UsernamePasswordAuthenticationToken auth(long id, boolean adminRole) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new Principal(id),
        null,
        AuthorityUtils.createAuthorityList(adminRole ? "ROLE_ADMIN" : "ROLE_USER"));
  }

  private String json(Object value) {
    return mapper.writeValueAsString(value);
  }

  private long member(String name, boolean isAdmin) {
    var member =
        members.saveAndFlush(
            Member.createVerified(
                prefix + "-" + created.size() + "@example.com",
                encoder.encode(PASSWORD),
                name,
                NOW));
    created.add(member.getId());
    if (isAdmin) jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", member.getId());
    return member.getId();
  }

  static class AdminClock extends Clock {
    final AtomicReference<Instant> value = new AtomicReference<>();

    void at(LocalDateTime time) {
      value.set(time.toInstant(ZoneOffset.UTC));
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return Clock.fixed(instant(), zone);
    }

    @Override
    public Instant instant() {
      return value.get();
    }
  }

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    AdminClock adminClock() {
      var clock = new AdminClock();
      clock.at(NOW);
      return clock;
    }
  }
}
