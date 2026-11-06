package com.example.lancerpick.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lancerpick.auth.dto.SignupEmailVerificationResponse;
import com.example.lancerpick.auth.mail.AccountMail;
import com.example.lancerpick.auth.service.AuthSubjectHasher;
import com.example.lancerpick.auth.service.SignupEmailService;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.member.dto.SignupRequest;
import com.example.lancerpick.member.service.MemberService;
import com.example.lancerpick.support.FakeAccountMailSender;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.support.TestMembers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 이메일 인증 가입: POST /api/auth/signup-email-requests → signup-email-verifications → POST /api/members(증명).
 * 실제 Security 필터 → Controller → Service → MySQL. 메일은 가짜 발송기가 받아 테스트가 링크 토큰을 꺼내 끝까지 진행한다.
 * 이 테스트가 만든 회원·인증 행(signup-email-it- 접두어)과 그 요청 제한 행만 정리한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(FakeAccountMailSender.Config.class)
class SignupEmailIntegrationTests {
    private static final String EMAIL_PREFIX = "signup-email-it-";
    private static final String PASSWORD = "abcd1234";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeAccountMailSender mails;
    @Autowired AuthSubjectHasher hasher;
    @Autowired SignupEmailService signupEmailService;
    @Autowired MemberService memberService;

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<String> usedEmails = new ArrayList<>();

    @BeforeEach
    void resetFakeMailer() {
        mails.reset();
    }

    @AfterEach
    void removeOnlyThisTestsRows() {
        for (String email : usedEmails) {
            for (String purpose : List.of("SIGNUP_EMAIL", "PASSWORD_RESET")) {
                jdbc.update("DELETE FROM auth_request_limits WHERE subject_hmac = ?", (Object) hasher.subject(purpose, email));
            }
        }
        jdbc.update("DELETE FROM account_challenges WHERE email LIKE ?", EMAIL_PREFIX + "%");
        List<Long> ids = jdbc.queryForList("SELECT id FROM members WHERE email LIKE ?", Long.class, EMAIL_PREFIX + "%");
        for (Long id : ids) jdbc.update("DELETE FROM wallet_entries WHERE member_id = ?", id);
        for (Long id : ids) jdbc.update("DELETE FROM wallets WHERE member_id = ?", id);
        for (Long id : ids) jdbc.update("DELETE FROM members WHERE id = ?", id);
    }

    @Test
    void fullFlowCreatesAVerifiedMemberWithWalletOnlyAfterVerification() throws Exception {
        String email = newEmail();

        requestMail(email).andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store"));
        AccountMail mail = onlyMail(email);
        assertThat(mail.subject()).isEqualTo("[랜서픽] 회원가입 이메일 인증 안내");
        assertThat(mail.body()).contains("http://localhost:5173/signup/verify#token=").contains("15분");
        // 인증 메일만으로는 회원·지갑·적립금이 생기지 않는다.
        assertThat(memberCount(email)).isZero();

        String token = FakeAccountMailSender.tokenIn(mail);
        JsonNode verified = body(verify(token).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.alreadyRegistered").value(false))
                .andExpect(jsonPath("$.proofExpiresAt").isNotEmpty()));
        String proof = verified.get("proof").asText();
        assertThat(proof).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(token);
        assertThat(memberCount(email)).isZero();

        // 메일 링크는 한 번만 쓸 수 있다.
        verify(token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIGNUP_EMAIL_TOKEN_INVALID"));

        signup(email, proof).andExpect(status().isCreated()).andExpect(jsonPath("$.email").value(email));
        long id = jdbc.queryForObject("SELECT id FROM members WHERE email = ?", Long.class, email);
        assertThat(jdbc.queryForObject("SELECT email_verified_at IS NOT NULL FROM members WHERE id = ?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT balance FROM wallets WHERE member_id = ?", Long.class, id)).isEqualTo(1_000_000L);
        assertThat(jdbc.queryForObject("SELECT consumed_at IS NOT NULL FROM account_challenges WHERE email = ?", Boolean.class,
                email)).isTrue();

        // 증명도 한 번만 쓸 수 있고, 로그인은 새 비밀번호로 된다(자동 로그인 없음).
        signup(email, proof).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void databaseKeepsOnlyHashesAndTheProofGetsItsOwnExpiry() throws Exception {
        String email = newEmail();
        requestMail(email);
        String token = FakeAccountMailSender.tokenIn(onlyMail(email));

        Map<String, Object> issued = challengeRow(email);
        assertThat(issued.get("purpose")).isEqualTo("SIGNUP_EMAIL");
        assertThat(issued.get("member_id")).isNull();
        assertThat(issued.get("credential_version")).isNull();
        assertThat((byte[]) issued.get("token_hash")).isEqualTo(sha256(token));
        assertThat(((Number) issued.get("ttl")).longValue()).isEqualTo(15 * 60);

        String proof = body(verify(token).andExpect(status().isOk())).get("proof").asText();
        Map<String, Object> verified = challengeRow(email);
        assertThat((byte[]) verified.get("token_hash")).isEqualTo(sha256(proof));
        assertThat(verified.get("verified_at")).isNotNull();
        assertThat(((Number) verified.get("proof_ttl")).longValue()).isEqualTo(30 * 60);
    }

    @Test
    void registeredEmailGetsTheSameRequestResponseAndLearnsItOnlyAfterVerifying() throws Exception {
        String registered = newEmail();
        mvc.perform(post("/api/members").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(TestMembers.signupJson(jdbc, registered, PASSWORD, "기존"))).andExpect(status().isCreated());
        String fresh = newEmail();

        MvcResult forRegistered = requestMail(registered).andReturn();
        MvcResult forFresh = requestMail(fresh).andReturn();
        assertThat(forRegistered.getResponse().getStatus()).isEqualTo(forFresh.getResponse().getStatus()).isEqualTo(202);
        assertThat(forRegistered.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(forFresh.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(mails.to(registered)).hasSize(1);
        assertThat(mails.to(fresh)).hasSize(1);

        String proof = body(verify(FakeAccountMailSender.tokenIn(onlyMail(registered)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alreadyRegistered").value(true))).get("proof").asText();
        signup(registered, proof).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
        assertThat(memberCount(registered)).isEqualTo(1);
    }

    @Test
    void expiredLinkAndExpiredProofAreRejected() throws Exception {
        String email = newEmail();
        requestMail(email);
        String token = FakeAccountMailSender.tokenIn(onlyMail(email));
        shiftChallengeIntoThePast(email);
        verify(token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIGNUP_EMAIL_TOKEN_INVALID"));

        String other = newEmail();
        requestMail(other);
        String proof = body(verify(FakeAccountMailSender.tokenIn(onlyMail(other))).andExpect(status().isOk())).get("proof").asText();
        shiftChallengeIntoThePast(other);
        signup(other, proof).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));
        assertThat(memberCount(other)).isZero();
    }

    @Test
    void proofCannotBeUsedForAnotherEmailOrSkippedOrMixedWithPasswordReset() throws Exception {
        String email = newEmail();
        requestMail(email);
        String token = FakeAccountMailSender.tokenIn(onlyMail(email));

        // 인증하지 않은 메일 링크 토큰을 증명으로 보냄
        signup(email, token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));
        String proof = body(verify(token).andExpect(status().isOk())).get("proof").asText();
        // 다른 이메일로 가입 시도
        String other = newEmail();
        signup(other, proof).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));
        assertThat(memberCount(other)).isZero();
        // 가입 증명을 비밀번호 재설정 토큰으로 사용
        mvc.perform(post("/api/auth/password-resets").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + proof + "\",\"newPassword\":\"reset5678x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));

        // 비밀번호 재설정 메일 토큰을 가입 인증·가입 증명으로 사용
        signup(email, proof).andExpect(status().isCreated());
        mvc.perform(post("/api/auth/password-reset-requests").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}")).andExpect(status().isAccepted());
        String resetToken = FakeAccountMailSender.tokenIn(mails.to(email).getLast());
        verify(resetToken).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIGNUP_EMAIL_TOKEN_INVALID"));
        String another = newEmail();
        signup(another, resetToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));
    }

    @Test
    void malformedTokensAndProofsGetTheSameErrors() throws Exception {
        String email = newEmail();
        String unknown = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        for (String bad : List.of("", "short", "+".repeat(43), unknown)) {
            verify(bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIGNUP_EMAIL_TOKEN_INVALID"));
            signup(email, bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));
        }
        // 증명 없는 예전 형식의 가입 요청은 받지 않는다.
        mvc.perform(post("/api/members").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"구형\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(memberCount(email)).isZero();
    }

    @Test
    void secondRequestWithinSixtySecondsSendsNothingAndResetHasItsOwnLimit() throws Exception {
        String email = newEmail();
        requestMail(email).andExpect(status().isAccepted());
        requestMail(email).andExpect(status().isAccepted());
        assertThat(mails.to(email)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_challenges WHERE email = ?", Integer.class, email))
                .isEqualTo(1);

        requestMail("bad-email").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        requestMail(" " + email.toUpperCase() + " ").andExpect(status().isAccepted());
        assertThat(mails.to(email)).hasSize(1);   // 정규화된 같은 이메일이라 같은 제한을 받는다
    }

    @Test
    void sameLinkVerifiedConcurrentlySucceedsOnlyOnce() throws Exception {
        String email = newEmail();
        requestMail(email);
        String token = FakeAccountMailSender.tokenIn(onlyMail(email));

        List<String> outcomes = race(2, () -> {
            try {
                signupEmailService.verify(token);
                return "OK";
            } catch (BusinessException e) {
                return e.code();
            }
        });
        assertThat(outcomes).containsExactlyInAnyOrder("OK", "SIGNUP_EMAIL_TOKEN_INVALID");
    }

    @Test
    void sameProofUsedConcurrentlyCreatesOneMember() throws Exception {
        String email = newEmail();
        requestMail(email);
        SignupEmailVerificationResponse verified = signupEmailService.verify(FakeAccountMailSender.tokenIn(onlyMail(email)));

        List<String> outcomes = race(2, () -> {
            try {
                memberService.signup(new SignupRequest(email, PASSWORD, "동시", verified.proof()));
                return "OK";
            } catch (BusinessException e) {
                return e.code();
            }
        });
        assertThat(outcomes).containsExactlyInAnyOrder("OK", "EMAIL_VERIFICATION_INVALID");
        assertThat(memberCount(email)).isEqualTo(1);
        long id = jdbc.queryForObject("SELECT id FROM members WHERE email = ?", Long.class, email);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entries WHERE member_id = ?", Long.class, id)).isEqualTo(1L);
    }

    @Test
    void twoProofsForTheSameEmailUsedConcurrentlyCreateOneMember() throws Exception {
        String email = newEmail();
        String first = TestMembers.verifiedProof(jdbc, email);
        String second = TestMembers.verifiedProof(jdbc, email);

        List<String> proofs = List.of(first, second);
        AtomicInteger next = new AtomicInteger();   // 두 스레드가 서로 다른 증명을 하나씩 가져간다
        List<String> outcomes = race(2, () -> {
            try {
                memberService.signup(new SignupRequest(email, PASSWORD, "동시", proofs.get(next.getAndIncrement())));
                return "OK";
            } catch (BusinessException e) {
                return e.code();
            }
        });
        assertThat(outcomes).containsExactlyInAnyOrder("OK", "EMAIL_ALREADY_EXISTS");
        assertThat(memberCount(email)).isEqualTo(1);
        // 진 쪽의 증명 사용 처리는 롤백된다.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_challenges WHERE email = ? AND consumed_at IS NOT NULL",
                Integer.class, email)).isEqualTo(1);
    }

    @Test
    void csrfIsRequiredEvenWithoutLogin() throws Exception {
        String email = newEmail();
        mvc.perform(post("/api/auth/signup-email-requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/auth/signup-email-verifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + "x".repeat(43) + "\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertThat(mails.sent).isEmpty();
    }

    // ---- helpers ----

    private String newEmail() {
        String email = EMAIL_PREFIX + UUID.randomUUID().toString().substring(0, 12) + "@example.com";
        usedEmails.add(email);
        return email;
    }

    private ResultActions requestMail(String email) throws Exception {
        return mvc.perform(post("/api/auth/signup-email-requests").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions verify(String token) throws Exception {
        return mvc.perform(post("/api/auth/signup-email-verifications").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    private ResultActions signup(String email, String proof) throws Exception {
        return mvc.perform(post("/api/members").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"인증가입\""
                        + ",\"emailVerificationProof\":\"" + proof + "\"}"));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private AccountMail onlyMail(String email) {
        List<AccountMail> forEmail = mails.to(email);
        assertThat(forEmail).hasSize(1);
        return forEmail.getFirst();
    }

    private Map<String, Object> challengeRow(String email) {
        return jdbc.queryForMap("SELECT purpose, member_id, credential_version, token_hash, verified_at, "
                + "TIMESTAMPDIFF(SECOND, issued_at, expires_at) AS ttl, "
                + "TIMESTAMPDIFF(SECOND, verified_at, expires_at) AS proof_ttl "
                + "FROM account_challenges WHERE email = ?", email);
    }

    /** 발급·인증·만료 시각을 모두 1시간 앞으로 옮겨 "만료된 상태"를 만든다(테스트 DB 픽스처, 시각 순서 CHECK 유지). */
    private void shiftChallengeIntoThePast(String email) {
        jdbc.update("UPDATE account_challenges SET expires_at = DATE_SUB(expires_at, INTERVAL 1 HOUR), "
                + "issued_at = DATE_SUB(issued_at, INTERVAL 1 HOUR), "
                + "verified_at = DATE_SUB(verified_at, INTERVAL 1 HOUR) WHERE email = ?", email);
    }

    private int memberCount(String email) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM members WHERE email = ?", Integer.class, email);
    }

    private static byte[] sha256(String token) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(Base64.getUrlDecoder().decode(token));
    }

    /** 같은 작업을 threads개 스레드에서 동시에 시작하고 결과를 모은다. */
    private static List<String> race(int threads, Callable<String> work) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return work.call();
                }));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) outcomes.add(future.get(30, TimeUnit.SECONDS));
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
