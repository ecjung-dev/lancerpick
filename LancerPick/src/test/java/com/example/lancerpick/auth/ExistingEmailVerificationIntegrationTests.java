package com.example.lancerpick.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lancerpick.auth.mail.AccountMail;
import com.example.lancerpick.auth.service.AuthSubjectHasher;
import com.example.lancerpick.auth.service.ExistingEmailVerificationService;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.support.FakeAccountMailSender;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.support.TestMembers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 기존 회원 이메일 인증(POST /api/me/email-verification-requests, /api/me/email-verifications).
 * 실제 Security 필터 → Controller → Service → MySQL. 기존 회원은 가입 후 email_verified_at을 NULL로 되돌려 만든다.
 * 메일은 가짜 발송기가 받고 테스트가 링크의 토큰을 꺼낸다. 이 테스트가 만든 회원(exist-email-it- 접두어)과 그 행만 정리한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(FakeAccountMailSender.Config.class)
class ExistingEmailVerificationIntegrationTests {
  private static final String EMAIL_PREFIX = "exist-email-it-";
  private static final String PASSWORD = "abcd1234";
  private static final Pattern TOKEN_IN_LINK = Pattern.compile("/mypage/email-verification#token=([A-Za-z0-9_-]{43})");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired FakeAccountMailSender mails;
  @Autowired AuthSubjectHasher hasher;
  @Autowired ExistingEmailVerificationService verificationService;

  private final List<String> usedEmails = new ArrayList<>();

  @BeforeEach
  void resetFakeMailer() {
    mails.reset();
  }

  @AfterEach
  void removeOnlyThisTestsRows() {
    for (String email : usedEmails)
      jdbc.update("DELETE FROM auth_request_limits WHERE subject_hmac = ?", (Object) hasher.subject("EXISTING_EMAIL", email));
    jdbc.update("DELETE FROM account_challenges WHERE email LIKE ?", EMAIL_PREFIX + "%");
    List<Long> ids = jdbc.queryForList("SELECT id FROM members WHERE email LIKE ?", Long.class, EMAIL_PREFIX + "%");
    for (Long id : ids) jdbc.update("DELETE FROM wallet_entries WHERE member_id = ?", id);
    for (Long id : ids) jdbc.update("DELETE FROM wallets WHERE member_id = ?", id);
    for (Long id : ids) jdbc.update("DELETE FROM members WHERE id = ?", id);
  }

  @Test
  void existingMemberVerifiesOwnEmailWithoutLosingSessionOrChangingCredentials() throws Exception {
    String email = legacyMember();
    long authVersionBefore = authVersion(email);
    MockHttpSession session = login(email);
    me(session).andExpect(jsonPath("$.emailVerified").value(false));

    request(session).andExpect(status().isAccepted()).andExpect(header().string("Cache-Control", "no-store"));
    AccountMail mail = onlyMail(email);
    assertThat(mail.subject()).isEqualTo("[랜서픽] 이메일 인증 안내");
    assertThat(mail.body()).contains("http://localhost:5173/mypage/email-verification#token=").contains("15분")
        .doesNotContain(PASSWORD);
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM account_challenges WHERE purpose='EXISTING_EMAIL' AND email=? AND member_id IS NOT NULL AND credential_version=?",
        Long.class, email, authVersionBefore)).isEqualTo(1L);

    verify(session, tokenIn(mail)).andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"));

    // 세션은 그대로이고 인증 상태만 바뀐다. 비밀번호·인증 버전은 바뀌지 않는다.
    me(session).andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
    assertThat(authVersion(email)).isEqualTo(authVersionBefore);
    assertThat(jdbc.queryForObject("SELECT consumed_at IS NOT NULL FROM account_challenges WHERE purpose='EXISTING_EMAIL' AND email=?",
        Boolean.class, email)).isTrue();
    // 같은 링크를 다시 쓰면 400, 이미 인증된 회원의 새 요청은 409.
    verify(session, tokenIn(mail)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));
    request(session).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_VERIFIED"));
  }

  @Test
  void anotherAccountsLinkDoesNotVerifyEitherAccount() throws Exception {
    String owner = legacyMember();
    String other = legacyMember();
    request(login(owner)).andExpect(status().isAccepted());
    String token = tokenIn(onlyMail(owner));

    MockHttpSession otherSession = login(other);
    verify(otherSession, token).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));
    assertThat(verifiedAt(owner)).isNull();
    assertThat(verifiedAt(other)).isNull();
    // 거절된 토큰은 소비되지 않아 본인은 계속 쓸 수 있다.
    verify(login(owner), token).andExpect(status().isNoContent());
    assertThat(verifiedAt(owner)).isNotNull();
  }

  @Test
  void expiredMalformedOrCredentialChangedTokensAreRejectedTheSameWay() throws Exception {
    String email = legacyMember();
    MockHttpSession session = login(email);
    request(session).andExpect(status().isAccepted());
    String token = tokenIn(onlyMail(email));

    verify(session, "short").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));
    verify(session, "A".repeat(43)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));

    // 발급 뒤 비밀번호가 바뀐 것과 같은 상태(발급 당시 버전과 다름).
    jdbc.update("UPDATE account_challenges SET credential_version = credential_version + 7 WHERE email = ?", email);
    verify(session, token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));
    jdbc.update("UPDATE account_challenges SET credential_version = credential_version - 7 WHERE email = ?", email);

    // 만료: 저장된 발급·만료 시각을 함께 1시간 앞당긴다(유효 15분 → 이미 지남). 두 시각의 차이가 유지돼
    // ck_challenge_time(만료 > 발급)을 지킨다. 비밀번호 재설정 테스트와 같은 방식.
    jdbc.update("UPDATE account_challenges SET expires_at = DATE_SUB(expires_at, INTERVAL 1 HOUR), "
        + "issued_at = DATE_SUB(issued_at, INTERVAL 1 HOUR) WHERE purpose = 'EXISTING_EMAIL' AND email = ?", email);
    verify(session, token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));
    assertThat(verifiedAt(email)).isNull();
  }

  @Test
  void requestsAreLimitedPer60SecondsAndNeedLoginAndCsrf() throws Exception {
    String email = legacyMember();
    MockHttpSession session = login(email);
    for (int i = 0; i < 3; i++) request(session).andExpect(status().isAccepted());
    assertThat(mails.to(email)).as("60초 간격 제한").hasSize(1);

    mvc.perform(post("/api/me/email-verification-requests").with(csrf())).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/me/email-verification-requests").session(session)).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    mvc.perform(post("/api/me/email-verifications").session(session).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void concurrentUseOfTheSameLinkVerifiesOnce() throws Exception {
    String email = legacyMember();
    MockHttpSession session = login(email);
    request(session).andExpect(status().isAccepted());
    String token = tokenIn(onlyMail(email));

    // 서비스를 두 스레드에서 동시에 부른다(MockMvc 세션 공유 대신). 회원 잠금으로 한 줄로 서고 늦은 쪽은 "이미 사용됨".
    long memberId = jdbc.queryForObject("SELECT id FROM members WHERE email = ?", Long.class, email);
    Callable<String> attempt = () -> {
      try {
        verificationService.verify(memberId, token);
        return "OK";
      } catch (BusinessException e) {
        return e.code();
      }
    };
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(attempt);
      var second = pool.submit(attempt);
      assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder("OK", "EMAIL_VERIFICATION_INVALID");
    }
    assertThat(verifiedAt(email)).isNotNull();
  }

  // ---------- 도우미 ----------

  /** 가입 API로 만든 뒤 인증 시각을 지워 "이메일 인증 없이 가입한 기존 회원"을 만든다. */
  private String legacyMember() throws Exception {
    String email = EMAIL_PREFIX + UUID.randomUUID().toString().substring(0, 12) + "@example.com";
    usedEmails.add(email);
    mvc.perform(post("/api/members").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(TestMembers.signupJson(jdbc, email, PASSWORD, "기존 회원")))
        .andExpect(status().isCreated());
    jdbc.update("UPDATE members SET email_verified_at = NULL WHERE email = ?", email);
    return email;
  }

  private MockHttpSession login(String email) throws Exception {
    var result = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk()).andReturn();
    MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
    assertThat(session).isNotNull();
    return session;
  }

  private ResultActions me(MockHttpSession session) throws Exception {
    return mvc.perform(get("/api/me").session(session));
  }

  private ResultActions request(MockHttpSession session) throws Exception {
    return mvc.perform(post("/api/me/email-verification-requests").session(session).with(csrf()));
  }

  private ResultActions verify(MockHttpSession session, String token) throws Exception {
    return mvc.perform(post("/api/me/email-verifications").session(session).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"));
  }

  private AccountMail onlyMail(String email) {
    List<AccountMail> sent = mails.to(email);
    assertThat(sent).hasSize(1);
    return sent.getFirst();
  }

  private static String tokenIn(AccountMail mail) {
    Matcher matcher = TOKEN_IN_LINK.matcher(mail.body());
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private long authVersion(String email) {
    return jdbc.queryForObject("SELECT auth_version FROM members WHERE email = ?", Long.class, email);
  }

  private Object verifiedAt(String email) {
    return jdbc.queryForObject("SELECT email_verified_at FROM members WHERE email = ?", Object.class, email);
  }
}
