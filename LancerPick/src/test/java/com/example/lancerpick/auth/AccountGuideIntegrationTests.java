package com.example.lancerpick.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lancerpick.auth.mail.AccountMail;
import com.example.lancerpick.auth.service.AuthSubjectHasher;
import com.example.lancerpick.support.FakeAccountMailSender;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.support.TestMembers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 아이디 안내(POST /api/auth/account-guide-requests). 실제 Security 필터 → Controller → Service → MySQL.
 * 메일은 가짜 발송기가 받는다. 이 테스트가 만든 회원(id-guide-it- 접두어)과 그 제한 행만 정리한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(FakeAccountMailSender.Config.class)
class AccountGuideIntegrationTests {
  private static final String EMAIL_PREFIX = "id-guide-it-";
  private static final String PASSWORD = "abcd1234";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired FakeAccountMailSender mails;
  @Autowired AuthSubjectHasher hasher;

  private final List<String> usedEmails = new ArrayList<>();

  @BeforeEach
  void resetFakeMailer() {
    mails.reset();
  }

  @AfterEach
  void removeOnlyThisTestsRows() {
    for (String email : usedEmails) {
      jdbc.update("DELETE FROM auth_request_limits WHERE subject_hmac = ?", (Object) hasher.subject("ACCOUNT_GUIDE", email));
      jdbc.update("DELETE FROM auth_request_limits WHERE subject_hmac = ?", (Object) hasher.subject("PASSWORD_RESET", email));
    }
    jdbc.update("DELETE FROM account_challenges WHERE email LIKE ?", EMAIL_PREFIX + "%");
    List<Long> ids = jdbc.queryForList("SELECT id FROM members WHERE email LIKE ?", Long.class, EMAIL_PREFIX + "%");
    for (Long id : ids) jdbc.update("DELETE FROM wallet_entries WHERE member_id = ?", id);
    for (Long id : ids) jdbc.update("DELETE FROM wallets WHERE member_id = ?", id);
    for (Long id : ids) jdbc.update("DELETE FROM members WHERE id = ?", id);
  }

  @Test
  void registeredEmailGetsAGuideWithLoginAndResetLinksButNoTokenOrRecord() throws Exception {
    String email = signupMember();
    long challengesBefore = challengeCount(email);

    requestGuide("  " + email.toUpperCase() + "  ").andExpect(status().isAccepted())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.message").value("입력한 주소가 가입한 이메일이면 아이디 안내 메일을 보냅니다."));

    List<AccountMail> sent = mails.to(email);
    assertThat(sent).hasSize(1);
    AccountMail mail = sent.getFirst();
    assertThat(mail.subject()).isEqualTo("[랜서픽] 아이디 안내");
    assertThat(mail.tracked()).as("토큰이 없는 안내 메일은 DB에 발송 결과를 남기지 않는다").isFalse();
    assertThat(mail.body())
        .contains("http://localhost:5173/login")
        .contains("http://localhost:5173/password-reset/request")
        .doesNotContain("#token=").doesNotContain(PASSWORD).doesNotContain("아이디 테스트");
    assertThat(challengeCount(email)).as("account_challenges 행을 만들지 않는다").isEqualTo(challengesBefore);
  }

  @Test
  void unknownEmailGetsTheSameResponseAndNoMail() throws Exception {
    String known = signupMember();
    String unknown = EMAIL_PREFIX + "none-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    usedEmails.add(unknown);

    MvcResult forKnown = requestGuide(known).andReturn();
    MvcResult forUnknown = requestGuide(unknown).andReturn();

    assertThat(forUnknown.getResponse().getStatus()).isEqualTo(forKnown.getResponse().getStatus()).isEqualTo(202);
    assertThat(forUnknown.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .isEqualTo(forKnown.getResponse().getContentAsString(StandardCharsets.UTF_8));
    assertThat(mails.to(unknown)).isEmpty();
    assertThat(mails.to(known)).hasSize(1);
  }

  @Test
  void repeatedRequestsWithin60SecondsSendOnlyOnceAndDoNotTouchPasswordResetLimits() throws Exception {
    String email = signupMember();
    for (int i = 0; i < 3; i++) requestGuide(email).andExpect(status().isAccepted());
    assertThat(mails.to(email)).as("같은 이메일 60초 간격 제한").hasSize(1);

    // 아이디 안내 제한은 비밀번호 재설정 제한과 따로 센다.
    mvc.perform(post("/api/auth/password-reset-requests").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
        .andExpect(status().isAccepted());
    assertThat(mails.to(email)).hasSize(2);
  }

  @Test
  void mailFailureIsNotShownAndBadInputOrMissingCsrfIsRejected() throws Exception {
    String email = signupMember();
    mails.mode = "THROW";
    requestGuide(email).andExpect(status().isAccepted());

    for (String bad : List.of("not-an-email", "")) {
      requestGuide(bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
    mvc.perform(post("/api/auth/account-guide-requests").contentType(MediaType.APPLICATION_JSON).content("{}").with(csrf()))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/auth/account-guide-requests").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\"}"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
  }

  private String signupMember() throws Exception {
    String email = EMAIL_PREFIX + UUID.randomUUID().toString().substring(0, 12) + "@example.com";
    usedEmails.add(email);
    mvc.perform(post("/api/members").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(TestMembers.signupJson(jdbc, email, PASSWORD, "아이디 테스트")))
        .andExpect(status().isCreated());
    return email;
  }

  private ResultActions requestGuide(String email) throws Exception {
    return mvc.perform(post("/api/auth/account-guide-requests").with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"));
  }

  private long challengeCount(String email) {
    Long count = jdbc.queryForObject("SELECT COUNT(*) FROM account_challenges WHERE email = ?", Long.class, email);
    return count == null ? 0 : count;
  }
}
