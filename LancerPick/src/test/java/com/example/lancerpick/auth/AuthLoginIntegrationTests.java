package com.example.lancerpick.auth;

import com.example.lancerpick.support.TestMembers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 실제 Security 필터 → AuthController/MeController → Service → MySQL 로그인 검사.
 * 회원은 실제 회원가입 API로 만들고, 이 테스트가 만든 회원(login-it- 접두어)만 정리한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class AuthLoginIntegrationTests {
    private static final String EMAIL_PREFIX = "login-it-";
    private static final String PASSWORD = "abcd1234";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void removeOnlyThisTestsMembers() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM members WHERE email LIKE ?", Long.class, EMAIL_PREFIX + "%");
        for (Long id : ids) jdbc.update("DELETE FROM wallet_entries WHERE member_id = ?", id);
        for (Long id : ids) jdbc.update("DELETE FROM wallets WHERE member_id = ?", id);
        for (Long id : ids) jdbc.update("DELETE FROM members WHERE id = ?", id);
    }

    @Test
    void loginSucceedsAndSessionIsUsedByMe() throws Exception {
        String email = signupMember("로그인");
        Long id = jdbc.queryForObject("SELECT id FROM members WHERE email = ?", Long.class, email);

        MvcResult result = login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(String.valueOf(id)))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.hasSellerProfile").value(false))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        MockHttpSession session = sessionOf(result);

        mvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(String.valueOf(id)))
                .andExpect(jsonPath("$.displayName").value("로그인"));
    }

    @Test
    void sessionPrincipalExposesMemberIdAndRoleAuthority() throws Exception {
        String email = signupMember("관리자");
        Long id = jdbc.queryForObject("SELECT id FROM members WHERE email = ?", Long.class, email);
        jdbc.update("UPDATE members SET role = 'ADMIN' WHERE id = ?", id);

        MockHttpSession session = sessionOf(login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn());

        Authentication authentication = savedAuthentication(session);
        assertThat(authentication.getPrincipal()).isInstanceOf(AuthenticatedMember.class);
        assertThat(((AuthenticatedMember) authentication.getPrincipal()).memberId()).isEqualTo(id);
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
        assertThat(authentication.getCredentials()).isNull();
    }

    @Test
    void existingSessionIdIsChangedOnLogin() throws Exception {
        String email = signupMember("세션");
        MockHttpSession session = new MockHttpSession();
        String before = session.getId();

        mvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(email, PASSWORD)))
                .andExpect(status().isOk());

        assertThat(session.getId()).isNotEqualTo(before);
    }

    @Test
    void emailIsNormalizedBeforeLookup() throws Exception {
        String email = signupMember("정규화");
        login("  " + email.toUpperCase() + " ", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnTheSameError() throws Exception {
        String email = signupMember("실패");

        MvcResult wrongPassword = login(email, "wrong1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn();
        MvcResult unknownEmail = login(EMAIL_PREFIX + "nobody-" + UUID.randomUUID() + "@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn();

        // 가입 여부가 드러나지 않도록 두 실패의 메시지가 같아야 한다.
        assertThat(message(wrongPassword)).isEqualTo(message(unknownEmail));
        assertThat(savedContextOrNull(wrongPassword)).isNull();
    }

    @Test
    void passwordIsNotTrimmed() throws Exception {
        String email = signupMember("공백");
        login(email, " " + PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void missingFieldsAreRejected() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void loginWithoutCsrfTokenIsForbidden() throws Exception {
        String email = signupMember("CSRF");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body(email, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void patchMeChangesDisplayNameOfLoggedInMemberOnly() throws Exception {
        String email = signupMember("변경전");
        MockHttpSession session = sessionOf(login(email, PASSWORD).andExpect(status().isOk()).andReturn());

        mvc.perform(patch("/api/me").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"  변경후  \"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.displayName").value("변경후"));
        assertThat(jdbc.queryForObject("SELECT display_name FROM members WHERE email = ?", String.class, email))
                .isEqualTo("변경후");

        mvc.perform(patch("/api/me").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(jdbc.queryForObject("SELECT display_name FROM members WHERE email = ?", String.class, email))
                .isEqualTo("변경후");
    }

    @Test
    void logoutEndsSession() throws Exception {
        String email = signupMember("로그아웃");
        MockHttpSession session = sessionOf(login(email, PASSWORD).andExpect(status().isOk()).andReturn());

        mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void anonymousCannotUseMeOrLogout() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/me").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"익명\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").with(csrf())).andExpect(status().isUnauthorized());
    }

    private String signupMember(String displayName) throws Exception {
        String email = EMAIL_PREFIX + UUID.randomUUID().toString().substring(0, 12) + "@example.com";
        mvc.perform(post("/api/members").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(TestMembers.signupJson(jdbc, email, PASSWORD, displayName)))
                .andExpect(status().isCreated());
        return email;
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(email, password)));
    }

    private static String body(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static MockHttpSession sessionOf(MvcResult result) {
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private static SecurityContext savedContextOrNull(MvcResult result) {
        var session = result.getRequest().getSession(false);
        if (session == null) return null;
        return (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    }

    private static Authentication savedAuthentication(MockHttpSession session) {
        SecurityContext context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context).isNotNull();
        return context.getAuthentication();
    }

    private static String message(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        int start = json.indexOf("\"message\":\"") + 11;
        return json.substring(start, json.indexOf('"', start));
    }
}
