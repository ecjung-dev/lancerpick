package com.example.lancerpick.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.wallet.gateway.TossGatewayException;
import com.example.lancerpick.wallet.gateway.TossPaymentGateway;
import com.example.lancerpick.wallet.gateway.TossPaymentGateway.Payment;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Actual signup/login/CSRF + MySQL; only the external payment provider is replaced. */
@IntegrationTest
@AutoConfigureMockMvc
class SessionTopupFailureIntegrationTests {
    private static final long INITIAL = 1_000_000;
    private static final long AMOUNT = 50_000;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @MockitoBean TossPaymentGateway gateway;
    private final List<Long> members = new ArrayList<>();

    record Account(long id, String email, MockHttpSession session) {}
    record Topup(String id, String orderId, String key) {}

    @AfterEach
    void cleanup() {
        for (long id : members) {
            jdbc.update("DELETE FROM wallet_entries WHERE member_id=?", id);
            jdbc.update("DELETE FROM wallet_topups WHERE member_id=?", id);
            jdbc.update("DELETE FROM wallets WHERE member_id=?", id);
            jdbc.update("DELETE FROM members WHERE id=?", id);
        }
    }

    @Test
    void completedRefreshRepeatedApprovalAndReconcileNeverCreditTwice() throws Exception {
        var owner = register();
        var topup = create(owner);
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenReturn(payment(topup, "DONE"));
        confirm(owner, topup).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        for (int repeat = 0; repeat < 3; repeat++) {
            read(owner, path(topup)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
            read(owner, "/api/me/wallet/entries").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
            confirm(owner, topup).andExpect(status().isOk());
            write(owner, path(topup) + "/reconcile", null).andExpect(status().isOk());
        }
        funds(owner, topup, INITIAL + AMOUNT, 1);
        verify(gateway, times(1)).confirm(eq(topup.key()), eq(topup.orderId()), eq(AMOUNT), anyString());
        verify(gateway, never()).lookup(anyString());
    }

    @Test
    void leavingCheckoutWithoutApprovalKeepsReadyAndDoesNotCredit() throws Exception {
        var owner = register();
        var topup = create(owner);
        // Browser fail/cancel return has no server-side cancellation authority.
        for (int repeat = 0; repeat < 3; repeat++) {
            read(owner, path(topup) + "?code=PAY_PROCESS_CANCELED&status=COMPLETED")
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
        }
        write(owner, path(topup) + "/reconcile", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
        funds(owner, topup, INITIAL, 0);
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELED", "ABORTED", "EXPIRED"})
    void providerConfirmedFailureEndsWithoutCredit(String providerStatus) throws Exception {
        var owner = register();
        var topup = create(owner);
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenReturn(payment(topup, providerStatus));
        confirm(owner, topup).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
        read(owner, path(topup)).andExpect(jsonPath("$.status").value("FAILED"));
        confirm(owner, topup).andExpect(status().isConflict());
        write(owner, path(topup) + "/reconcile", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
        funds(owner, topup, INITIAL, 0);
        verify(gateway, times(1)).confirm(anyString(), anyString(), anyLong(), anyString());
        verify(gateway, never()).lookup(anyString());
    }

    @Test
    void timeoutCanBeReconciledAfterReloginWithoutRepeatingApproval() throws Exception {
        var owner = register();
        var topup = create(owner);
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenThrow(new TossGatewayException());
        confirm(owner, topup).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("UNKNOWN"));
        confirm(owner, topup).andExpect(status().isAccepted());
        funds(owner, topup, INITIAL, 0);
        write(owner, "/api/auth/logout", null).andExpect(status().isNoContent());
        var anonymous = new Account(0, "", new MockHttpSession());
        read(anonymous, path(topup)).andExpect(status().isUnauthorized());
        write(anonymous, path(topup) + "/reconcile", null).andExpect(status().isUnauthorized());
        var resumed = login(owner.id(), owner.email(), new MockHttpSession());
        when(gateway.lookup(topup.key())).thenReturn(payment(topup, "DONE"));
        write(resumed, path(topup) + "/reconcile", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        write(resumed, path(topup) + "/reconcile", null).andExpect(status().isOk());
        funds(resumed, topup, INITIAL + AMOUNT, 1);
        verify(gateway, times(1)).confirm(anyString(), anyString(), anyLong(), anyString());
        verify(gateway, times(1)).lookup(topup.key());
    }

    @Test
    void repeatedLookupTimeoutKeepsUnknownAndFundsUnchangedUntilDone() throws Exception {
        var owner = register();
        var topup = create(owner);
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenThrow(new TossGatewayException());
        confirm(owner, topup).andExpect(status().isAccepted());
        when(gateway.lookup(topup.key())).thenThrow(new TossGatewayException()).thenReturn(payment(topup, "DONE"));
        write(owner, path(topup) + "/reconcile", null).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("UNKNOWN"));
        funds(owner, topup, INITIAL, 0);
        write(owner, path(topup) + "/reconcile", null).andExpect(status().isOk());
        funds(owner, topup, INITIAL + AMOUNT, 1);
        verify(gateway, times(1)).confirm(anyString(), anyString(), anyLong(), anyString());
        verify(gateway, times(2)).lookup(topup.key());
    }

    @Test
    void invalidatedSessionAndOtherMemberCannotApproveOrReconcile() throws Exception {
        var owner = register();
        var other = register();
        var topup = create(owner);
        // Deterministically reproduce the state after server session expiration.
        owner.session().invalidate();
        var expired = new Account(0, "", new MockHttpSession());
        read(expired, path(topup)).andExpect(status().isUnauthorized());
        confirm(expired, topup).andExpect(status().isUnauthorized());
        write(expired, path(topup) + "/reconcile", null).andExpect(status().isUnauthorized());
        read(other, path(topup)).andExpect(status().isNotFound());
        confirm(other, topup).andExpect(status().isNotFound());
        write(other, path(topup) + "/reconcile", null).andExpect(status().isNotFound());
        var resumed = login(owner.id(), owner.email(), new MockHttpSession());
        read(resumed, path(topup)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
        funds(resumed, topup, INITIAL, 0);
        verifyNoInteractions(gateway);
    }

    private Account register() throws Exception {
        String email = "topup-session-" + UUID.randomUUID() + "@example.com";
        var anonymous = new Account(0, email, new MockHttpSession());
        var member = json(write(anonymous, "/api/members", Map.of("email", email, "password", "abcd1234", "displayName", "topup-session"))
                .andExpect(status().isCreated()));
        long id = Long.parseLong(member.get("id").asText());
        members.add(id);
        return login(id, email, anonymous.session());
    }

    private Account login(long id, String email, MockHttpSession session) throws Exception {
        var account = new Account(id, email, session);
        write(account, "/api/auth/login", Map.of("email", email, "password", "abcd1234")).andExpect(status().isOk());
        return account;
    }

    private Topup create(Account owner) throws Exception {
        var body = json(write(owner, "/api/me/wallet/topups", Map.of("amount", AMOUNT)).andExpect(status().isCreated()));
        return new Topup(body.get("id").asText(), body.get("tossOrderId").asText(), "test_payment_" + body.get("id").asText());
    }

    private ResultActions confirm(Account owner, Topup topup) throws Exception {
        return write(owner, path(topup) + "/confirm", Map.of("paymentKey", topup.key(), "orderId", topup.orderId(), "amount", AMOUNT));
    }

    private ResultActions write(Account owner, String path, Object body) throws Exception {
        var csrf = json(read(owner, "/api/auth/csrf").andExpect(status().isOk()));
        var request = post(path).session(owner.session()).header(csrf.get("headerName").asText(), csrf.get("token").asText());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        return mvc.perform(request);
    }

    private ResultActions read(Account owner, String path) throws Exception { return mvc.perform(get(path).session(owner.session())); }
    private JsonNode json(ResultActions response) throws Exception { return mapper.readTree(response.andReturn().getResponse().getContentAsString()); }
    private static String path(Topup topup) { return "/api/me/wallet/topups/" + topup.id(); }
    private static Payment payment(Topup topup, String status) {
        return new Payment(topup.key(), topup.orderId(), AMOUNT, AMOUNT, "KRW", status, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void funds(Account owner, Topup topup, long expectedBalance, long expectedCredits) throws Exception {
        read(owner, "/api/me/wallet").andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(expectedBalance));
        assertThat(jdbc.queryForObject("SELECT balance FROM wallets WHERE member_id=?", Long.class, owner.id())).isEqualTo(expectedBalance);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entries WHERE topup_id=? AND type='TOPUP'", Long.class, topup.id()))
                .isEqualTo(expectedCredits);
    }
}
