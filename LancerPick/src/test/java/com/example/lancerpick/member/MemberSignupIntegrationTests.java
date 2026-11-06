package com.example.lancerpick.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.support.TestMembers;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 실제 Security 필터 → Controller → MemberService → 지갑 초기화 → MySQL(V1) 회원가입 검사.
 * 각 요청은 실제로 커밋되므로 이 테스트가 만든 회원(signup-it- 접두어)만 정리한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class MemberSignupIntegrationTests {
    private static final String EMAIL_PREFIX = "signup-it-";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void removeOnlyThisTestsMembers() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM members WHERE email LIKE ?", Long.class, EMAIL_PREFIX + "%");
        for (Long id : ids) jdbc.update("DELETE FROM wallet_entries WHERE member_id = ?", id);
        for (Long id : ids) jdbc.update("DELETE FROM wallets WHERE member_id = ?", id);
        for (Long id : ids) jdbc.update("DELETE FROM members WHERE id = ?", id);
        TestMembers.deleteSignupProofs(jdbc, "%" + EMAIL_PREFIX + "%");
    }

    @Test
    void signupCreatesMemberWalletAndInitialGrantInOneRequest() throws Exception {
        String email = uniqueEmail();

        signup("  " + email.toUpperCase() + " ", "abcd1234", "  가입 테스트  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("가입 테스트"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.hasSellerProfile").value(false))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        Long id = jdbc.queryForObject("SELECT id FROM members WHERE email = ?", Long.class, email);
        String hash = jdbc.queryForObject("SELECT password_hash FROM members WHERE id = ?", String.class, id);
        assertThat(hash).startsWith("{bcrypt}").isNotEqualTo("abcd1234");
        assertThat(jdbc.queryForObject("SELECT balance FROM wallets WHERE member_id = ?", Long.class, id)).isEqualTo(1_000_000L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM wallet_entries WHERE member_id = ? AND type = 'INITIAL_GRANT' AND amount_delta = 1000000",
                Long.class, id)).isEqualTo(1L);
        // 이메일 인증을 마친 가입이므로 인증 시각이 가입 시각으로 기록된다.
        assertThat(jdbc.queryForObject("SELECT email_verified_at = created_at FROM members WHERE id = ?", Boolean.class, id))
                .isTrue();
    }

    @Test
    void roleInRequestBodyIsIgnored() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/members").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\",\"displayName\":\"권한\",\"role\":\"ADMIN\""
                                + ",\"emailVerificationProof\":\"" + TestMembers.verifiedProof(jdbc, email) + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));
        assertThat(jdbc.queryForObject("SELECT role FROM members WHERE email = ?", String.class, email)).isEqualTo("USER");
    }

    @Test
    void duplicateEmailAfterNormalizationReturns409AndCreatesNothingNew() throws Exception {
        String email = uniqueEmail();
        signup(email, "abcd1234", "처음").andExpect(status().isCreated());

        signup(" " + email.toUpperCase(), "abcd1234", "두번째")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM members WHERE email = ?", Long.class, email)).isEqualTo(1L);
        Long id = jdbc.queryForObject("SELECT id FROM members WHERE email = ?", Long.class, email);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallets WHERE member_id = ?", Long.class, id)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entries WHERE member_id = ?", Long.class, id)).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc1234", "abcdefgh", "12345678", "abcd 1234", " abcd1234", "비밀번호1234a"})
    void invalidPasswordIsRejectedWithPasswordFieldError(String password) throws Exception {
        String email = uniqueEmail();
        signup(email, password, "비밀번호")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
        assertNoMember(email);
    }

    @Test
    void passwordOver64CharactersIsRejected() throws Exception {
        String email = uniqueEmail();
        signup(email, "a1".repeat(32) + "b", "긴비밀번호")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertNoMember(email);
    }

    @ParameterizedTest
    @ValueSource(strings = {"@domain..com", "@-domain.com", "@domain", "@domain.c"})
    void invalidEmailFormatIsRejected(String domainPart) throws Exception {
        String email = EMAIL_PREFIX + UUID.randomUUID().toString().substring(0, 8) + domainPart;
        signup(email, "abcd1234", "이메일")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM members WHERE email LIKE ?", Long.class,
                email.substring(0, email.indexOf('@')) + "%")).isZero();
    }

    @Test
    void localPartWithLeadingOrConsecutiveDotIsRejected() throws Exception {
        String base = EMAIL_PREFIX + UUID.randomUUID().toString().substring(0, 8);
        signup("." + base + "@example.com", "abcd1234", "점").andExpect(status().isBadRequest());
        signup(base + "..x@example.com", "abcd1234", "점").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM members WHERE email LIKE ?", Long.class,
                "%" + base + "%")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "1234567890123456789012345678901"})
    void invalidDisplayNameIsRejected(String displayName) throws Exception {
        String email = uniqueEmail();
        signup(email, "abcd1234", displayName)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertNoMember(email);
    }

    @Test
    void missingFieldsAreRejected() throws Exception {
        mvc.perform(post("/api/members").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void signupWithoutCsrfTokenIsForbiddenAndCreatesNothing() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, "abcd1234", "CSRF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertNoMember(email);
    }

    /** 매번 이 이메일에 묶인 새 가입 증명을 만들어 함께 보낸다(TestMembers). */
    private ResultActions signup(String email, String password, String displayName) throws Exception {
        return mvc.perform(post("/api/members").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(TestMembers.signupJson(jdbc, email, password, displayName)));
    }

    private static String body(String email, String password, String displayName) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"displayName\":\"" + displayName + "\"}";
    }

    private static String uniqueEmail() {
        return EMAIL_PREFIX + UUID.randomUUID().toString().substring(0, 12) + "@example.com";
    }

    private void assertNoMember(String email) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM members WHERE email = ?", Long.class,
                email.trim().toLowerCase())).isZero();
    }
}
