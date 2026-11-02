package com.example.lancerpick.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.wallet.service.WalletInitializationService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 실제 Security 필터→Controller→Service→MySQL 검사. 로그인 자체는 테스트 principal로 대체한다. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class WalletTopupApiIntegrationTests {
    private static final AtomicLong IDS = new AtomicLong(850_000);
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired WalletInitializationService initialization;
    @Autowired JsonMapper mapper;

    record TestMember(Long memberId) implements AuthenticatedMember {}

    private static UsernamePasswordAuthenticationToken memberAuthentication(long id) {
        return UsernamePasswordAuthenticationToken.authenticated(new TestMember(id), null,
                AuthorityUtils.createAuthorityList("ROLE_USER"));
    }

    @Test
    void createAndReadUseAuthenticatedOwnerAndLeaveBalanceAndLedgerUntouched() throws Exception {
        long owner = member(true);
        long other = member(true);
        var response = mvc.perform(post("/api/me/wallet/topups").with(authentication(memberAuthentication(owner)))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50000,\"memberId\":" + other + ",\"status\":\"COMPLETED\",\"tossOrderId\":\"forged_order\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.amount").value(50000)).andExpect(jsonPath("$.currency").value("KRW"))
                .andExpect(jsonPath("$.isTest").value(true)).andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.memberId").doesNotExist()).andExpect(jsonPath("$.approvalRequestKey").doesNotExist())
                .andExpect(jsonPath("$.paymentKey").doesNotExist()).andReturn();
        var json = mapper.readTree(response.getResponse().getContentAsString());
        assertThat(json.get("id").isString()).isTrue();
        String id = json.get("id").asText();
        assertThat(response.getResponse().getHeader("Location")).isEqualTo("/api/me/wallet/topups/" + id);
        assertThat(json.get("tossOrderId").asText()).startsWith("topup_").hasSize(42);
        assertThat(Instant.parse(json.get("createdAt").asText())).isBeforeOrEqualTo(Instant.now());
        assertThat(jdbc.queryForObject("SELECT member_id FROM wallet_topups WHERE id=?", Long.class, id)).isEqualTo(owner);
        assertThat(jdbc.queryForObject("SELECT balance FROM wallets WHERE member_id=?", Long.class, owner)).isEqualTo(1000000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entries WHERE member_id=?", Long.class, owner)).isEqualTo(1);
        mvc.perform(get("/api/me/wallet/topups/" + id).with(authentication(memberAuthentication(owner))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.status").value("READY"));
        mvc.perform(get("/api/me/wallet/topups/" + id).with(authentication(memberAuthentication(other))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"amount\":null}", "{\"amount\":0}", "{\"amount\":-1}",
            "{\"amount\":1000001}", "{\"amount\":1.5}", "{\"amount\":9223372036854775808}", "{\"amount\":\"invalid\"}"})
    void invalidAmountCannotCreateRequest(String body) throws Exception {
        long owner = member(true);
        mvc.perform(post("/api/me/wallet/topups").with(authentication(memberAuthentication(owner)))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(count(owner)).isZero();
    }

    @Test
    void anonymousAndMissingCsrfCannotWrite() throws Exception {
        long owner = member(true);
        mvc.perform(post("/api/me/wallet/topups").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":50000}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/wallet/topups").with(authentication(memberAuthentication(owner)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":50000}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(get("/api/me/wallet/topups/1")).andExpect(status().isUnauthorized());
        assertThat(count(owner)).isZero();
    }

    @Test
    void missingWalletDoesNotInitializeOrGrantCredits() throws Exception {
        long owner = member(false);
        mvc.perform(post("/api/me/wallet/topups").with(authentication(memberAuthentication(owner)))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":50000}"))
                .andExpect(status().isNotFound());
        assertThat(count(owner)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallets WHERE member_id=?", Long.class, owner)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "wrong", "9223372036854775808"})
    void invalidLookupIdIsBadRequest(String id) throws Exception {
        long owner = member(true);
        mvc.perform(get("/api/me/wallet/topups/" + id).with(authentication(memberAuthentication(owner))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void eachRequestGetsSeparateIdentifiersWithoutAdditionalCredits() throws Exception {
        long owner = member(true);
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/me/wallet/topups").with(authentication(memberAuthentication(owner)))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1}"))
                    .andExpect(status().isCreated());
        }
        assertThat(count(owner)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT toss_order_id) FROM wallet_topups WHERE member_id=?", Long.class, owner)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT approval_request_key) FROM wallet_topups WHERE member_id=?", Long.class, owner)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT balance FROM wallets WHERE member_id=?", Long.class, owner)).isEqualTo(1000000);
    }

    @Test
    void disabledProviderDoesNotStartProcessingOrCredit() throws Exception {
        long owner = member(true);
        org.springframework.test.context.transaction.TestTransaction.flagForCommit();
        org.springframework.test.context.transaction.TestTransaction.end();
        try {
            var created = mvc.perform(post("/api/me/wallet/topups").with(authentication(memberAuthentication(owner)))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":50000}"))
                    .andExpect(status().isCreated()).andReturn();
            var json = mapper.readTree(created.getResponse().getContentAsString());
            String id = json.get("id").asText();
            String body = "{\"paymentKey\":\"test-payment\",\"orderId\":\"" + json.get("tossOrderId").asText() + "\",\"amount\":50000}";
            mvc.perform(post("/api/me/wallet/topups/" + id + "/confirm").with(authentication(memberAuthentication(owner)))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PAYMENT_PROVIDER_UNAVAILABLE"));
            assertThat(jdbc.queryForObject("SELECT status FROM wallet_topups WHERE id=?", String.class, id)).isEqualTo("READY");
            assertThat(jdbc.queryForObject("SELECT balance FROM wallets WHERE member_id=?", Long.class, owner)).isEqualTo(1000000);
        } finally {
            jdbc.update("DELETE FROM wallet_entries WHERE member_id=?", owner);
            jdbc.update("DELETE FROM wallet_topups WHERE member_id=?", owner);
            jdbc.update("DELETE FROM wallets WHERE member_id=?", owner);
            jdbc.update("DELETE FROM members WHERE id=?", owner);
        }
    }

    private long member(boolean withWallet) {
        long id = IDS.incrementAndGet();
        var now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        jdbc.update("INSERT INTO members(id,email,password_hash,display_name,role,created_at,updated_at) VALUES (?,?,?,'충전 API 테스트','USER',?,?)",
                id, "topup-api-" + id + "@example.test", "test-only-not-a-real-hash", now, now);
        if (withWallet) initialization.initializeForNewMember(id);
        return id;
    }

    private long count(long memberId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM wallet_topups WHERE member_id=?", Long.class, memberId);
    }
}
