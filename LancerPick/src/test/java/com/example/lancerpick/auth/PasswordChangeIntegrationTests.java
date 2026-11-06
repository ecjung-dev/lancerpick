package com.example.lancerpick.auth;

import com.example.lancerpick.support.TestMembers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.security.LoginMember;
import com.example.lancerpick.member.dto.ChangePasswordRequest;
import com.example.lancerpick.member.service.MemberService;
import com.example.lancerpick.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * POST /api/me/password와 세션 폐기(auth_version) 검사.
 * 실제 Security 필터 → MeController → MemberService → MySQL. 회원은 실제 가입 API로 만들고
 * 이 테스트가 만든 회원(pw-change-it- 접두어)만 정리한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class PasswordChangeIntegrationTests {
    private static final String EMAIL_PREFIX = "pw-change-it-";
    private static final String PASSWORD = "abcd1234";
    private static final String NEW_PASSWORD = "newpass5678";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberService memberService;

    @AfterEach
    void removeOnlyThisTestsMembers() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM members WHERE email LIKE ?", Long.class, EMAIL_PREFIX + "%");
        for (Long id : ids) jdbc.update("DELETE FROM wallet_entries WHERE member_id = ?", id);
        for (Long id : ids) jdbc.update("DELETE FROM wallets WHERE member_id = ?", id);
        for (Long id : ids) jdbc.update("DELETE FROM members WHERE id = ?", id);
    }

    @Test
    void loginStoresTheCurrentAuthVersionInThePrincipal() throws Exception {
        String email = signupMember();
        MockHttpSession session = sessionOf(login(email, PASSWORD).andExpect(status().isOk()).andReturn());

        SecurityContext context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getPrincipal()).isInstanceOfSatisfying(LoginMember.class,
                member -> assertThat(member.authVersion()).isEqualTo(0L));
    }

    @Test
    void changeEndsEverySessionAndOnlyTheNewPasswordWorks() throws Exception {
        String email = signupMember();
        long id = memberId(email);
        String oldHash = passwordHash(email);
        long balanceBefore = walletBalance(id);
        long entriesBefore = walletEntryCount(id);
        MockHttpSession sessionA = sessionOf(login(email, PASSWORD).andExpect(status().isOk()).andReturn());
        MockHttpSession sessionB = sessionOf(login(email, PASSWORD).andExpect(status().isOk()).andReturn());

        changePassword(sessionA, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));

        // 변경한 세션은 바로 끝난다.
        assertThat(sessionA.isInvalid()).isTrue();
        // 다른 기기·탭의 세션은 다음 요청에서 폐기된다.
        mvc.perform(get("/api/me").session(sessionB))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        assertThat(sessionB.isInvalid()).isTrue();

        login(email, PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        MockHttpSession fresh = sessionOf(login(email, NEW_PASSWORD).andExpect(status().isOk()).andReturn());
        mvc.perform(get("/api/me").session(fresh)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        assertThat(authVersion(id)).isEqualTo(1L);
        assertThat(passwordHash(email)).startsWith("{bcrypt}").isNotEqualTo(oldHash).doesNotContain(NEW_PASSWORD);
        // 비밀번호 변경은 지갑을 다시 만들거나 적립금을 지급하지 않는다.
        assertThat(walletBalance(id)).isEqualTo(balanceBefore);
        assertThat(walletEntryCount(id)).isEqualTo(entriesBefore);
    }

    @Test
    void wrongCurrentPasswordChangesNothingAndKeepsTheSession() throws Exception {
        String email = signupMember();
        long id = memberId(email);
        MockHttpSession session = sessionOf(login(email, PASSWORD).andExpect(status().isOk()).andReturn());

        changePassword(session, "wrong1234", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        // 앞에 공백을 붙인 현재 비밀번호도 정리하지 않으므로 틀린 값이다.
        changePassword(session, " " + PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));
        // BCrypt 한도(72바이트)를 넘는 입력도 500이 아니라 불일치로 처리한다.
        changePassword(session, "가".repeat(30), NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));

        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk());
        assertThat(authVersion(id)).isZero();
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void newPasswordEqualToCurrentIsRejected() throws Exception {
        String email = signupMember();
        MockHttpSession session = sessionOf(login(email, PASSWORD).andExpect(status().isOk()).andReturn());

        changePassword(session, PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_UNCHANGED"));
        assertThat(authVersion(memberId(email))).isZero();
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc1234", "abcdefgh", "12345678", "abcd 1234", " abcd5678", "비밀번호1234a"})
    void newPasswordMustFollowTheSignupPolicy(String newPassword) throws Exception {
        String email = signupMember();
        MockHttpSession session = sessionOf(login(email, PASSWORD).andExpect(status().isOk()).andReturn());

        changePassword(session, PASSWORD, newPassword)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
        assertThat(authVersion(memberId(email))).isZero();
    }

    @Test
    void missingFieldsAreRejected() throws Exception {
        String email = signupMember();
        MockHttpSession session = sessionOf(login(email, PASSWORD).andExpect(status().isOk()).andReturn());

        for (String body : List.of("{}", "{\"currentPassword\":\"" + PASSWORD + "\"}", "{\"newPassword\":\"" + NEW_PASSWORD + "\"}")) {
            mvc.perform(post("/api/me/password").session(session).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        assertThat(authVersion(memberId(email))).isZero();
    }

    @Test
    void anonymousAndMissingCsrfAreRejected() throws Exception {
        mvc.perform(post("/api/me/password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        String email = signupMember();
        MockHttpSession session = sessionOf(login(email, PASSWORD).andExpect(status().isOk()).andReturn());
        mvc.perform(post("/api/me/password").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(body(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertThat(authVersion(memberId(email))).isZero();
    }

    @Test
    void sessionWithAnOldAuthVersionIsDroppedButPublicPagesStillWork() throws Exception {
        String email = signupMember();
        long id = memberId(email);
        MockHttpSession stale = sessionOf(login(email, PASSWORD).andExpect(status().isOk()).andReturn());
        MockHttpSession staleForPublic = sessionOf(login(email, PASSWORD).andExpect(status().isOk()).andReturn());
        MockHttpSession staleForChange = sessionOf(login(email, PASSWORD).andExpect(status().isOk()).andReturn());
        // 다른 서버·기기에서 비밀번호가 바뀐 상황을 만든다(테스트 DB 픽스처).
        jdbc.update("UPDATE members SET auth_version = auth_version + 1 WHERE id = ?", id);

        mvc.perform(get("/api/me").session(stale))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        assertThat(stale.isInvalid()).isTrue();

        // 공개 조회는 비로그인으로 계속 처리된다. 세션은 폐기된다.
        mvc.perform(get("/api/categories").session(staleForPublic)).andExpect(status().isOk());
        assertThat(staleForPublic.isInvalid()).isTrue();

        // 옛 세션은 현재 비밀번호를 알아도 비밀번호를 바꿀 수 없다(CSRF가 맞아도 401).
        changePassword(staleForChange, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        assertThat(authVersion(id)).isEqualTo(1L);
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void deletedMemberSessionIsDropped() throws Exception {
        String email = signupMember();
        long id = memberId(email);
        MockHttpSession session = sessionOf(login(email, PASSWORD).andExpect(status().isOk()).andReturn());
        jdbc.update("DELETE FROM wallet_entries WHERE member_id = ?", id);
        jdbc.update("DELETE FROM wallets WHERE member_id = ?", id);
        jdbc.update("DELETE FROM members WHERE id = ?", id);

        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void concurrentChangesFromTheSameSessionVersionLetOnlyOneWin() throws Exception {
        String email = signupMember();
        long id = memberId(email);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (String next : List.of("first1111", "second2222")) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        memberService.changePassword(id, 0L, new ChangePasswordRequest(PASSWORD, next));
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
            assertThat(outcomes).contains("AUTHENTICATION_REQUIRED");
            assertThat(authVersion(id)).isEqualTo(1L);
            String winner = outcomes.stream().filter(o -> o.startsWith("OK:")).findFirst().orElseThrow().substring(3);
            login(email, winner).andExpect(status().isOk());
            login(email, PASSWORD).andExpect(status().isUnauthorized());
        } finally {
            pool.shutdownNow();
        }
    }

    private String signupMember() throws Exception {
        String email = EMAIL_PREFIX + UUID.randomUUID().toString().substring(0, 12) + "@example.com";
        mvc.perform(post("/api/members").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(TestMembers.signupJson(jdbc, email, PASSWORD, "비번변경")))
                .andExpect(status().isCreated());
        return email;
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions changePassword(MockHttpSession session, String current, String next) throws Exception {
        return mvc.perform(post("/api/me/password").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(current, next)));
    }

    private static String body(String current, String next) {
        return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
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

    private String passwordHash(String email) {
        return jdbc.queryForObject("SELECT password_hash FROM members WHERE email = ?", String.class, email);
    }

    private long walletBalance(long id) {
        return jdbc.queryForObject("SELECT balance FROM wallets WHERE member_id = ?", Long.class, id);
    }

    private long walletEntryCount(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entries WHERE member_id = ?", Long.class, id);
    }
}
