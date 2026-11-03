package com.example.lancerpick.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.json.JsonMapper;

/** 테스트 principal을 주입하지 않고 회원가입·로그인으로 발급한 세션을 사용한다. */
class WalletSessionIntegrationTests extends AuthLoginIntegrationTests {
    private final JsonMapper mapper=JsonMapper.builder().build();
    private MockHttpSession loginNewMember() throws Exception {
        String email="login-it-wallet-"+UUID.randomUUID()+"@example.com";
        mvc.perform(post("/api/members").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\""+email+"\",\"password\":\"abcd1234\",\"displayName\":\"wallet-session\"}")).andExpect(status().isCreated());
        var result=mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\""+email+"\",\"password\":\"abcd1234\"}")).andExpect(status().isOk()).andReturn();
        return (MockHttpSession)result.getRequest().getSession(false);
    }
    @Test void realSessionReturnsOnlyItsOwnGrantAndIgnoresSpoofedMemberId() throws Exception {
        var first=loginNewMember();var second=loginNewMember();
        var secondMe=mvc.perform(get("/api/me").session(second)).andExpect(status().isOk()).andReturn();
        String secondId=mapper.readTree(secondMe.getResponse().getContentAsString()).get("id").asText();
        var firstEntries=mvc.perform(get("/api/me/wallet/entries").session(first).param("memberId",secondId))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].type").value("INITIAL_GRANT"))
                .andExpect(jsonPath("$.items[0].amountDelta").value(1000000)).andReturn();
        var secondEntries=mvc.perform(get("/api/me/wallet/entries").session(second)).andExpect(status().isOk()).andReturn();
        assertThat(mapper.readTree(firstEntries.getResponse().getContentAsString()).get("items").get(0).get("id").asText())
                .isNotEqualTo(mapper.readTree(secondEntries.getResponse().getContentAsString()).get("items").get(0).get("id").asText());
        mvc.perform(get("/api/me/wallet").session(first)).andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(1000000));
    }
    @Test void logoutRevokesWalletAccessAndWritesStillRequireCsrf() throws Exception {
        var session=loginNewMember();
        mvc.perform(post("/api/me/wallet/topups").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/wallet").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/wallet/entries").session(session)).andExpect(status().isUnauthorized());
    }
}
