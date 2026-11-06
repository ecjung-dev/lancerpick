package com.example.lancerpick.auth;

import com.example.lancerpick.support.TestMembers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lancerpick.member.repository.MemberRepository;
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
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 로그인 응답 조회(hasSellerProfile)가 실패하면 세션에 로그인 정보가 남지 않아야 한다.
 *
 * {@code @MockitoSpyBean}은 Spring 테스트 환경(컨텍스트)을 따로 하나 만든다. 컨텍스트마다 DB 연결 묶음이 열리므로
 * 이 클래스가 끝나면 @DirtiesContext로 그 컨텍스트를 닫아 연결을 돌려준다. (CI의 Too many connections 방지)
 * 다른 로그인 테스트가 이 클래스를 상속하지 않도록 AuthLoginIntegrationTests와 분리했다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@DirtiesContext
class AuthLoginResponseFailureIntegrationTests {
    private static final String EMAIL_PREFIX = "login-fail-it-";
    private static final String PASSWORD = "abcd1234";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    // 실제 Repository를 감싸서 특정 메서드만 실패시키는 용도(나머지는 진짜로 동작)
    @MockitoSpyBean MemberRepository members;

    @AfterEach
    void removeOnlyThisTestsMembers() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM members WHERE email LIKE ?", Long.class, EMAIL_PREFIX + "%");
        for (Long id : ids) jdbc.update("DELETE FROM wallet_entries WHERE member_id = ?", id);
        for (Long id : ids) jdbc.update("DELETE FROM wallets WHERE member_id = ?", id);
        for (Long id : ids) jdbc.update("DELETE FROM members WHERE id = ?", id);
    }

    @Test
    void responseLookupFailureLeavesNoLoggedInSession() throws Exception {
        String email = EMAIL_PREFIX + UUID.randomUUID().toString().substring(0, 12) + "@example.com";
        mvc.perform(post("/api/members").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(TestMembers.signupJson(jdbc, email, PASSWORD, "조회실패")))
                .andExpect(status().isCreated());
        doThrow(new IllegalStateException("db failure")).when(members).countSellerProfiles(anyLong());

        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isInternalServerError());

        // 응답이 실패했으면 서버 세션에도 로그인 정보가 남지 않아야 한다.
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
    }
}
