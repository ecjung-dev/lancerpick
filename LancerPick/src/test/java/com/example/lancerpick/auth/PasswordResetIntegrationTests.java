package com.example.lancerpick.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lancerpick.auth.mail.AccountMail;
import com.example.lancerpick.auth.mail.AccountMailDeliveryRecorder;
import com.example.lancerpick.auth.mail.AccountMailSender;
import com.example.lancerpick.auth.service.AuthSubjectHasher;
import com.example.lancerpick.auth.service.PasswordResetService;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.support.IntegrationTest;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 비밀번호 재설정(POST /api/auth/password-reset-requests, /api/auth/password-resets).
 * 실제 Security 필터 → Controller → Service → MySQL. 메일은 가짜 발송기(FakeMailSender)가 받아서
 * 테스트가 링크의 토큰을 꺼내 끝까지 진행한다. 이 테스트가 만든 회원(pw-reset-it- 접두어)과 그 제한 행만 정리한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class PasswordResetIntegrationTests {
    private static final String EMAIL_PREFIX = "pw-reset-it-";
    private static final String PASSWORD = "abcd1234";
    private static final String NEW_PASSWORD = "reset5678x";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("/password-reset#token=([A-Za-z0-9_-]{43})");

    /** C3 메일 인프라 대신 쓰는 가짜 발송기. 받은 메일을 모아 두고, mode로 큐 가득 참·예외를 흉내 낸다. */
    static class FakeMailSender implements AccountMailSender {
        final List<AccountMail> sent = new CopyOnWriteArrayList<>();
        volatile String mode = "ACCEPT";

        @Override
        public boolean enqueue(AccountMail mail) {
            if (mode.equals("THROW")) throw new IllegalStateException("smtp down");
            if (mode.equals("FULL")) return false;
            sent.add(mail);
            return true;
        }
    }

    @TestConfiguration
    static class FakeMailConfig {
        @Bean
        FakeMailSender fakeMailSender() {
            return new FakeMailSender();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeMailSender mails;
    @Autowired AuthSubjectHasher hasher;
    @Autowired PasswordResetService passwordResetService;
    @Autowired AccountMailDeliveryRecorder deliveryRecorder;

    private final List<String> usedEmails = new ArrayList<>();

    @BeforeEach
    void resetFakeMailer() {
        mails.sent.clear();
        mails.mode = "ACCEPT";
    }

    @AfterEach
    void removeOnlyThisTestsRows() {
        for (String email : usedEmails) {
            jdbc.update("DELETE FROM auth_request_limits WHERE subject_hmac = ?", (Object) subject(email));
        }
        jdbc.update("DELETE FROM account_challenges WHERE email LIKE ?", EMAIL_PREFIX + "%");
        List<Long> ids = jdbc.queryForList("SELECT id FROM members WHERE email LIKE ?", Long.class, EMAIL_PREFIX + "%");
        for (Long id : ids) jdbc.update("DELETE FROM wallet_entries WHERE member_id = ?", id);
        for (Long id : ids) jdbc.update("DELETE FROM wallets WHERE member_id = ?", id);
        for (Long id : ids) jdbc.update("DELETE FROM members WHERE id = ?", id);
    }

    @Test
    void fullResetFlowChangesThePasswordEndsSessionsAndKeepsTheWallet() throws Exception {
        String email = signupMember();
        long id = memberId(email);
        long balanceBefore = walletBalance(id);
        long entriesBefore = walletEntryCount(id);
        MockHttpSession oldSession = sessionOf(login(email, PASSWORD).andExpect(status().isOk()).andReturn());

        requestReset(email).andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store"));
        String token = onlyToken(email);

        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));

        // 기존 세션은 다음 요청에서 폐기된다(auth_version 증가).
        mvc.perform(get("/api/me").session(oldSession)).andExpect(status().isUnauthorized());
        login(email, PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login(email, NEW_PASSWORD).andExpect(status().isOk());

        assertThat(authVersion(id)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT consumed_at IS NOT NULL FROM account_challenges WHERE member_id = ?",
                Boolean.class, id)).isTrue();
        // 재설정은 지갑을 다시 만들거나 적립금을 지급하지 않는다.
        assertThat(walletBalance(id)).isEqualTo(balanceBefore);
        assertThat(walletEntryCount(id)).isEqualTo(entriesBefore);
    }

    @Test
    void mailLinkUsesTheConfiguredFrontendAndTheDatabaseKeepsOnlyTheHash() throws Exception {
        String email = signupMember();
        requestReset(email).andExpect(status().isAccepted());

        AccountMail mail = mails.sent.getFirst();
        assertThat(mail.to()).isEqualTo(email);
        assertThat(mail.body()).contains("http://localhost:5173/password-reset#token=").contains("15분")
                .doesNotContain(PASSWORD);
        assertThat(mail.toString()).doesNotContain(email).doesNotContain("token=");

        String token = onlyToken(email);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT purpose, email, token_hash, credential_version, delivery_status, "
                        + "TIMESTAMPDIFF(SECOND, issued_at, expires_at) AS ttl FROM account_challenges WHERE email = ?", email);
        assertThat(row.get("purpose")).isEqualTo("PASSWORD_RESET");
        assertThat(row.get("credential_version")).isEqualTo(0L);
        assertThat(row.get("delivery_status")).isEqualTo("PENDING");   // 큐에 넣었고 발송 결과는 아직
        assertThat(((Number) row.get("ttl")).longValue()).isEqualTo(15 * 60);
        byte[] expectedHash = MessageDigest.getInstance("SHA-256").digest(Base64.getUrlDecoder().decode(token));
        assertThat((byte[]) row.get("token_hash")).isEqualTo(expectedHash);

        // 발송기가 결과를 알리면 SENT. 두 번째 결과는 이미 기록된 값을 덮어쓰지 않는다.
        long challengeId = mail.challengeId();
        deliveryRecorder.markSent(challengeId);
        deliveryRecorder.markFailed(challengeId, "LATE_FAILURE");
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM account_challenges WHERE id = ?", String.class,
                challengeId)).isEqualTo("SENT");
    }

    @Test
    void unknownEmailGetsTheSameResponseAndNothingIsIssued() throws Exception {
        String known = signupMember();
        String unknown = EMAIL_PREFIX + "none-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        usedEmails.add(unknown);

        MvcResult forKnown = requestReset(known).andReturn();
        MvcResult forUnknown = requestReset(unknown).andReturn();

        assertThat(forUnknown.getResponse().getStatus()).isEqualTo(forKnown.getResponse().getStatus()).isEqualTo(202);
        assertThat(forUnknown.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo(forKnown.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("안내할 수 있는 경우");
        assertThat(forUnknown.getResponse().getHeader("Cache-Control"))
                .isEqualTo(forKnown.getResponse().getHeader("Cache-Control"));
        assertThat(mails.sent).extracting(AccountMail::to).containsExactly(known);
        assertThat(challengeCount(unknown)).isZero();
        // 가입하지 않은 이메일도 같은 요청 제한을 받는다.
        assertThat(limitRowCount(unknown)).isEqualTo(2);
    }

    @Test
    void emailIsNormalizedAndInvalidFormatIsRejected() throws Exception {
        String email = signupMember();
        requestReset("  " + email.toUpperCase() + " ").andExpect(status().isAccepted());
        assertThat(mails.sent).extracting(AccountMail::to).containsExactly(email);

        requestReset("not-an-email").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/auth/password-reset-requests").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void secondRequestWithinSixtySecondsSendsNothingButAnswersTheSame() throws Exception {
        String email = signupMember();
        requestReset(email).andExpect(status().isAccepted());
        requestReset(email).andExpect(status().isAccepted());

        assertThat(mails.sent).hasSize(1);
        assertThat(challengeCount(email)).isEqualTo(1);

        // 60초가 지나면(간격 행 만료) 다시 보낼 수 있다. 기존 링크는 지우지 않는다.
        expireInterval(email);
        requestReset(email).andExpect(status().isAccepted());
        assertThat(mails.sent).hasSize(2);
        assertThat(challengeCount(email)).isEqualTo(2);
    }

    @Test
    void atMostFiveRequestsPerHourAreIssued() throws Exception {
        String email = signupMember();
        for (int i = 0; i < 7; i++) {
            requestReset(email).andExpect(status().isAccepted());
            expireInterval(email);   // 60초 간격 제한은 따로 확인하므로 여기서는 지난 것으로 만든다
        }
        assertThat(mails.sent).hasSize(5);
        assertThat(challengeCount(email)).isEqualTo(5);
    }

    @Test
    void concurrentRequestsForTheSameEmailIssueOnlyOneLink() throws Exception {
        String email = signupMember();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    passwordResetService.requestReset(email);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) result.get(30, TimeUnit.SECONDS);   // 예외 없이 모두 끝나야 한다
        } finally {
            pool.shutdownNow();
        }
        assertThat(challengeCount(email)).isEqualTo(1);
        assertThat(mails.sent).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT request_count FROM auth_request_limits WHERE subject_hmac = ? AND scope = 'PASSWORD_RESET_EMAIL_HOUR'",
                Integer.class, (Object) subject(email))).isEqualTo(1);
    }

    @Test
    void tokenCanBeUsedOnlyOnce() throws Exception {
        String email = signupMember();
        requestReset(email);
        String token = onlyToken(email);

        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());
        resetPassword(token, "another999x").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));

        assertThat(authVersion(memberId(email))).isEqualTo(1L);
        login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void expiredTokenIsRejectedAndChangesNothing() throws Exception {
        String email = signupMember();
        requestReset(email);
        String token = onlyToken(email);
        jdbc.update("UPDATE account_challenges SET expires_at = DATE_SUB(expires_at, INTERVAL 1 HOUR), "
                + "issued_at = DATE_SUB(issued_at, INTERVAL 1 HOUR) WHERE email = ?", email);

        resetPassword(token, NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));
        assertThat(authVersion(memberId(email))).isZero();
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void usingOneLinkInvalidatesTheOtherOutstandingLinks() throws Exception {
        String email = signupMember();
        requestReset(email);
        expireInterval(email);
        requestReset(email);
        assertThat(mails.sent).hasSize(2);
        String first = tokenIn(mails.sent.get(0));
        String second = tokenIn(mails.sent.get(1));

        resetPassword(second, NEW_PASSWORD).andExpect(status().isNoContent());
        // 먼저 받은 링크는 발급 당시 auth_version(0)이 지금(1)과 달라 쓸 수 없다.
        resetPassword(first, "another999x").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));
        login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void passwordChangeAfterIssuingInvalidatesTheLink() throws Exception {
        String email = signupMember();
        requestReset(email);
        String token = onlyToken(email);
        // 링크를 받은 뒤 로그인 상태에서 비밀번호를 바꿨다.
        MockHttpSession session = sessionOf(login(email, PASSWORD).andExpect(status().isOk()).andReturn());
        mvc.perform(post("/api/me/password").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"changed123x\"}"))
                .andExpect(status().isNoContent());

        resetPassword(token, NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));
        login(email, "changed123x").andExpect(status().isOk());
    }

    @Test
    void malformedOrUnknownTokensGetTheSameError() throws Exception {
        String email = signupMember();
        String unknownButWellFormed = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        for (String token : List.of("", "short", "a".repeat(43) + "=", "+".repeat(43), unknownButWellFormed)) {
            resetPassword(token, NEW_PASSWORD).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));
        }
        assertThat(authVersion(memberId(email))).isZero();
    }

    @Test
    void newPasswordMustFollowTheSignupPolicyAndTheTokenStaysUsable() throws Exception {
        String email = signupMember();
        requestReset(email);
        String token = onlyToken(email);

        for (String weak : List.of("abc1234", "abcdefgh", "abcd 1234", "비밀번호1234a")) {
            resetPassword(token, weak).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
        }
        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void sameTokenSubmittedConcurrentlySucceedsOnlyOnce() throws Exception {
        String email = signupMember();
        requestReset(email);
        String token = onlyToken(email);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (String next : List.of("first1111x", "second2222x")) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        passwordResetService.resetPassword(token, next);
                        return "OK:" + next;
                    } catch (BusinessException e) {
                        return e.code();
                    }
                }));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) outcomes.add(result.get(30, TimeUnit.SECONDS));

            assertThat(outcomes).filteredOn(o -> o.startsWith("OK:")).hasSize(1);
            assertThat(outcomes).contains("PASSWORD_RESET_TOKEN_INVALID");
            assertThat(authVersion(memberId(email))).isEqualTo(1L);
            String winner = outcomes.stream().filter(o -> o.startsWith("OK:")).findFirst().orElseThrow().substring(3);
            login(email, winner).andExpect(status().isOk());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void mailHandOffFailuresAreRecordedButTheResponseStaysTheSame() throws Exception {
        String full = signupMember();
        mails.mode = "FULL";
        requestReset(full).andExpect(status().isAccepted());
        assertThat(deliveryOf(full)).isEqualTo("FAILED:MAIL_QUEUE_FULL");

        String broken = signupMember();
        mails.mode = "THROW";
        requestReset(broken).andExpect(status().isAccepted());
        assertThat(deliveryOf(broken)).isEqualTo("FAILED:MAIL_ENQUEUE_FAILED");
    }

    @Test
    void csrfIsRequiredEvenWithoutLogin() throws Exception {
        String email = signupMember();
        mvc.perform(post("/api/auth/password-reset-requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/auth/password-resets").contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody("x".repeat(43), NEW_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertThat(challengeCount(email)).isZero();
    }

    // ---- helpers ----

    private String signupMember() throws Exception {
        String email = EMAIL_PREFIX + UUID.randomUUID().toString().substring(0, 12) + "@example.com";
        usedEmails.add(email);
        mvc.perform(post("/api/members").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                                + "\",\"displayName\":\"재설정\"}"))
                .andExpect(status().isCreated());
        return email;
    }

    private ResultActions requestReset(String email) throws Exception {
        return mvc.perform(post("/api/auth/password-reset-requests").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions resetPassword(String token, String newPassword) throws Exception {
        return mvc.perform(post("/api/auth/password-resets").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(resetBody(token, newPassword)));
    }

    private static String resetBody(String token, String newPassword) {
        return "{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}";
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private String onlyToken(String email) {
        List<AccountMail> forEmail = mails.sent.stream().filter(m -> m.to().equals(email)).toList();
        assertThat(forEmail).hasSize(1);
        return tokenIn(forEmail.getFirst());
    }

    private static String tokenIn(AccountMail mail) {
        Matcher matcher = TOKEN_IN_LINK.matcher(mail.body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private byte[] subject(String email) {
        return hasher.subject("PASSWORD_RESET", email);
    }

    /** 간격 행의 만료 시각을 과거로 돌려 "60초가 지난 상태"를 만든다(테스트 DB 픽스처). */
    private void expireInterval(String email) {
        jdbc.update("UPDATE auth_request_limits SET expires_at = DATE_ADD(window_start, INTERVAL 1 SECOND) "
                + "WHERE subject_hmac = ? AND scope = 'PASSWORD_RESET_EMAIL_INTERVAL'", (Object) subject(email));
    }

    private static MockHttpSession sessionOf(MvcResult result) {
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private long memberId(String email) {
        return jdbc.queryForObject("SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    private long authVersion(long id) {
        return jdbc.queryForObject("SELECT auth_version FROM members WHERE id = ?", Long.class, id);
    }

    private int challengeCount(String email) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM account_challenges WHERE email = ?", Integer.class, email);
    }

    private int limitRowCount(String email) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM auth_request_limits WHERE subject_hmac = ?", Integer.class,
                (Object) subject(email));
    }

    private String deliveryOf(String email) {
        return jdbc.queryForObject(
                "SELECT CONCAT(delivery_status, ':', COALESCE(last_error_code, '')) FROM account_challenges WHERE email = ?",
                String.class, email);
    }

    private long walletBalance(long id) {
        return jdbc.queryForObject("SELECT balance FROM wallets WHERE member_id = ?", Long.class, id);
    }

    private long walletEntryCount(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entries WHERE member_id = ?", Long.class, id);
    }
}
