package com.example.lancerpick.wallet;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.wallet.dto.WalletTopupResponse;
import com.example.lancerpick.wallet.entity.WalletTopupStatus;
import com.example.lancerpick.wallet.gateway.TossGatewayException;
import com.example.lancerpick.wallet.gateway.TossPaymentGateway;
import com.example.lancerpick.wallet.gateway.TossPaymentGateway.Payment;
import com.example.lancerpick.wallet.repository.WalletEntryRepository;
import com.example.lancerpick.wallet.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 커밋·잠금·롤백을 사용하며 외부 제공자만 대체한다. 실 토스 승인 테스트와 구분한다. */
@IntegrationTest
@AutoConfigureMockMvc
class WalletTopupApprovalIntegrationTests {
    private static final AtomicLong IDS = new AtomicLong(950_000);
    private final List<Long> owners = new ArrayList<>();
    @Autowired JdbcTemplate jdbc;
    @Autowired WalletInitializationService initialization;
    @Autowired WalletTopupService topups;
    @Autowired WalletTopupApprovalService approvals;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MockMvc mvc;
    @MockitoBean TossPaymentGateway gateway;
    @MockitoSpyBean WalletEntryRepository entries;

    record TestMember(Long memberId) implements AuthenticatedMember {}

    @AfterEach
    void cleanup() {
        for (long id : owners) {
            jdbc.update("DELETE FROM wallet_entries WHERE member_id=?", id);
            jdbc.update("DELETE FROM wallet_topups WHERE member_id=?", id);
            jdbc.update("DELETE FROM wallets WHERE member_id=?", id);
            jdbc.update("DELETE FROM members WHERE id=?", id);
        }
    }

    @Test
    void approvalCommitsProcessingBeforeNetworkAndCreditsExactlyOnce() {
        long owner = member();
        var topup = topups.create(owner, 50000);
        String key = "payment_" + topup.id();
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(jdbc.queryForObject("SELECT status FROM wallet_topups WHERE id=?", String.class, topup.id())).isEqualTo("PROCESSING");
            assertThat(jdbc.queryForObject("SELECT payment_key FROM wallet_topups WHERE id=?", String.class, topup.id())).isEqualTo(key);
            assertThat(call.getArgument(3, String.class)).isEqualTo(jdbc.queryForObject(
                    "SELECT approval_request_key FROM wallet_topups WHERE id=?", String.class, topup.id()));
            return done(topup, key);
        });
        var result = confirm(owner, topup, key);
        assertThat(result.status()).isEqualTo(WalletTopupStatus.COMPLETED);
        assertThat(confirm(owner, topup, key).status()).isEqualTo(WalletTopupStatus.COMPLETED);
        assertThat(balance(owner)).isEqualTo(1050000);
        assertThat(credits(topup)).isEqualTo(1);
        verify(gateway, times(1)).confirm(eq(key), eq(topup.tossOrderId()), eq(50000L), anyString());
        String completed = jdbc.queryForObject("SELECT DATE_FORMAT(completed_at,'%Y-%m-%d %H:%i:%s.%f') FROM wallet_topups WHERE id=?", String.class, topup.id());
        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s.%f') FROM wallet_entries WHERE topup_id=?", String.class, topup.id())).isEqualTo(completed);
        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(updated_at,'%Y-%m-%d %H:%i:%s.%f') FROM wallets WHERE member_id=?", String.class, owner)).isEqualTo(completed);
    }

    @Test
    void timeoutIsUnknownAndLookupRecoversWithoutRepeatingApproval() {
        long owner = member();
        var topup = topups.create(owner, 50000);
        String key = "payment_" + topup.id();
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenThrow(new TossGatewayException());
        assertThat(confirm(owner, topup, key).status()).isEqualTo(WalletTopupStatus.UNKNOWN);
        assertThat(confirm(owner, topup, key).status()).isEqualTo(WalletTopupStatus.UNKNOWN);
        assertThat(balance(owner)).isEqualTo(1000000);
        when(gateway.lookup(key)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return done(topup, key);
        });
        assertThat(approvals.reconcile(owner, Long.parseLong(topup.id())).status()).isEqualTo(WalletTopupStatus.COMPLETED);
        approvals.reconcile(owner, Long.parseLong(topup.id()));
        assertThat(balance(owner)).isEqualTo(1050000);
        assertThat(credits(topup)).isEqualTo(1);
        verify(gateway, times(1)).confirm(anyString(), anyString(), anyLong(), anyString());
        verify(gateway, times(1)).lookup(key);
    }

    @Test
    void localLedgerInsertFailureRollsBackBalanceAndCompletionThenRecovers() {
        long owner = member();
        var topup = topups.create(owner, 50000);
        String key = "payment_" + topup.id();
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenReturn(done(topup, key));
        doAnswer(call -> {
            // 잔액 UPDATE 이후 실제 MySQL CHECK/FK 위반을 발생시킨다. SUPER·트리거 권한은 필요 없다.
            jdbc.update("""
                    INSERT INTO wallet_entries(member_id,topup_id,type,amount_delta,balance_after,event_key,created_at)
                    VALUES (?,?,'TOPUP',0,0,?,UTC_TIMESTAMP(6))
                    """, owner, topup.id(), "test-invalid:" + topup.id());
            return 0;
        }).when(entries).insertTopup(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), any());
        try {
            assertThat(confirm(owner, topup, key).status()).isEqualTo(WalletTopupStatus.UNKNOWN);
            assertThat(balance(owner)).isEqualTo(1000000);
            assertThat(credits(topup)).isZero();
            assertThat(jdbc.queryForObject("SELECT completed_at FROM wallet_topups WHERE id=?", Object.class, topup.id())).isNull();
        } finally {
            reset(entries);
        }
        when(gateway.lookup(key)).thenReturn(done(topup, key));
        assertThat(approvals.reconcile(owner, Long.parseLong(topup.id())).status()).isEqualTo(WalletTopupStatus.COMPLETED);
        assertThat(balance(owner)).isEqualTo(1050000);
        assertThat(credits(topup)).isEqualTo(1);
    }

    @Test
    void wrongOwnerAndChangedTermsNeverCallProvider() {
        long owner = member();
        long other = member();
        var topup = topups.create(owner, 50000);
        assertThatThrownBy(() -> confirm(other, topup, "payment_test")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> approvals.confirm(owner, Long.parseLong(topup.id()), "payment_test", topup.tossOrderId(), 1))
                .isInstanceOf(BusinessException.class);
        verify(gateway, never()).confirm(anyString(), anyString(), anyLong(), anyString());
        assertThat(balance(owner)).isEqualTo(1000000);
        assertThat(topups.get(owner, Long.parseLong(topup.id())).status()).isEqualTo(WalletTopupStatus.READY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AMOUNT", "ORDER", "KEY", "CURRENCY", "BALANCE", "APPROVED_AT"})
    void mismatchedExternalResultCannotCreditAndCanBeReconciled(String field) {
        long owner = member();
        var topup = topups.create(owner, 50000);
        String key = "payment_" + topup.id();
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenReturn(
                new Payment(field.equals("KEY") ? "other-payment" : key,
                        field.equals("ORDER") ? "other_order" : topup.tossOrderId(),
                        field.equals("AMOUNT") ? 1 : 50000, field.equals("BALANCE") ? 1 : 50000,
                        field.equals("CURRENCY") ? "USD" : "KRW", "DONE",
                        field.equals("APPROVED_AT") ? null : OffsetDateTime.now(ZoneOffset.UTC)));
        assertThatThrownBy(() -> confirm(owner, topup, key)).isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo("TOPUP_PAYMENT_MISMATCH"));
        assertThat(balance(owner)).isEqualTo(1000000);
        assertThat(credits(topup)).isZero();
        assertThat(topups.get(owner, Long.parseLong(topup.id())).status()).isEqualTo(WalletTopupStatus.UNKNOWN);
    }

    @RepeatedTest(3)
    void sameTopupConcurrentConfirmCallsProviderOnlyOnce() throws Exception {
        long owner = member();
        var topup = topups.create(owner, 50000);
        String key = "payment_" + topup.id();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout");
            return done(topup, key);
        });
        try {
            var first = executor.submit(() -> confirm(owner, topup, key));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> confirm(owner, topup, key));
            assertThat(second.get(10, TimeUnit.SECONDS).status()).isEqualTo(WalletTopupStatus.PROCESSING);
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).status()).isEqualTo(WalletTopupStatus.COMPLETED);
            assertThat(balance(owner)).isEqualTo(1050000);
            assertThat(credits(topup)).isEqualTo(1);
            verify(gateway, times(1)).confirm(anyString(), anyString(), anyLong(), anyString());
        } finally {
            release.countDown(); executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void differentTopupsOnSameWalletPreserveBothCredits() throws Exception {
        long owner = member();
        var first = topups.create(owner, 50000);
        var second = topups.create(owner, 60000);
        var barrier = new CyclicBarrier(2);
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenAnswer(call -> {
            barrier.await(10, TimeUnit.SECONDS);
            var topup = call.getArgument(1, String.class).equals(first.tossOrderId()) ? first : second;
            return done(topup, call.getArgument(0));
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> confirm(owner, first, "payment_" + first.id()));
            var b = executor.submit(() -> confirm(owner, second, "payment_" + second.id()));
            assertThat(a.get(15, TimeUnit.SECONDS).status()).isEqualTo(WalletTopupStatus.COMPLETED);
            assertThat(b.get(15, TimeUnit.SECONDS).status()).isEqualTo(WalletTopupStatus.COMPLETED);
            assertThat(balance(owner)).isEqualTo(1110000);
        } finally { executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS); }
    }

    @Test
    void httpPendingResponseIs202AndCsrfIsRequired() throws Exception {
        long owner = member();
        var topup = topups.create(owner, 50000);
        String key = "payment_" + topup.id();
        var auth = UsernamePasswordAuthenticationToken.authenticated(new TestMember(owner), null, AuthorityUtils.createAuthorityList("ROLE_USER"));
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenThrow(new TossGatewayException());
        String body = "{\"paymentKey\":\"" + key + "\",\"orderId\":\"" + topup.tossOrderId() + "\",\"amount\":50000}";
        mvc.perform(post("/api/me/wallet/topups/" + topup.id() + "/confirm").with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/me/wallet/topups/" + topup.id() + "/confirm").with(authentication(auth)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(balance(owner)).isEqualTo(1000000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABORTED", "EXPIRED", "CANCELED"})
    void verifiedTerminalProviderStateEndsWithoutCredit(String providerStatus) {
        long owner = member();
        var topup = topups.create(owner, 50000);
        String key = "payment_" + topup.id();
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenReturn(
                new Payment(key, topup.tossOrderId(), 50000, 0, "KRW", providerStatus, null));
        assertThat(confirm(owner, topup, key).status()).isEqualTo(WalletTopupStatus.FAILED);
        assertThat(balance(owner)).isEqualTo(1000000);
        assertThat(credits(topup)).isZero();
        assertThatThrownBy(() -> confirm(owner, topup, key)).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_PROGRESS", "WAITING_FOR_DEPOSIT", "PARTIAL_CANCELED"})
    void pendingAndPartialStatesNeverGrantCredits(String providerStatus) {
        long owner = member();
        var topup = topups.create(owner, 50000);
        String key = "payment_" + topup.id();
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenReturn(
                new Payment(key, topup.tossOrderId(), 50000, 50000, "KRW", providerStatus, null));
        assertThat(confirm(owner, topup, key).status()).isEqualTo(WalletTopupStatus.UNKNOWN);
        assertThat(balance(owner)).isEqualTo(1000000);
        assertThat(credits(topup)).isZero();
    }

    @Test
    void paymentKeyCannotBeBoundToAnotherRequest() {
        long owner = member();
        var first = topups.create(owner, 50000);
        var second = topups.create(owner, 50000);
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenReturn(done(first, "same-payment-key"));
        confirm(owner, first, "same-payment-key");
        assertThatThrownBy(() -> confirm(owner, second, "same-payment-key")).isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo("TOPUP_PAYMENT_KEY_CONFLICT"));
        assertThat(topups.get(owner, Long.parseLong(second.id())).status()).isEqualTo(WalletTopupStatus.READY);
        assertThat(balance(owner)).isEqualTo(1050000);
        verify(gateway, times(1)).confirm(anyString(), anyString(), anyLong(), anyString());
    }

    @RepeatedTest(3)
    void independentWalletsCanCreditConcurrentlyWithoutMissingRowGapLocks() throws Exception {
        var requests = new ArrayList<WalletTopupResponse>();
        var memberIds = new ArrayList<Long>();
        for (int i = 0; i < 4; i++) {
            long owner = member(); memberIds.add(owner); requests.add(topups.create(owner, 50000));
        }
        var barrier = new CyclicBarrier(4);
        when(gateway.confirm(anyString(), anyString(), anyLong(), anyString())).thenAnswer(call -> {
            barrier.await(10, TimeUnit.SECONDS);
            var topup = requests.stream().filter(item -> item.tossOrderId().equals(call.getArgument(1))).findFirst().orElseThrow();
            return done(topup, call.getArgument(0));
        });
        var executor = Executors.newFixedThreadPool(4);
        try {
            var futures = new ArrayList<Future<WalletTopupResponse>>();
            for (int i = 0; i < 4; i++) {
                long owner = memberIds.get(i); var topup = requests.get(i);
                futures.add(executor.submit(() -> confirm(owner, topup, "payment_" + topup.id())));
            }
            for (var future : futures) assertThat(future.get(15, TimeUnit.SECONDS).status()).isEqualTo(WalletTopupStatus.COMPLETED);
            for (long owner : memberIds) assertThat(balance(owner)).isEqualTo(1050000);
        } finally { executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS); }
    }

    private long member() {
        long id = IDS.incrementAndGet(); owners.add(id);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("INSERT INTO members(id,email,password_hash,display_name,role,created_at,updated_at) VALUES (?,?,?,'승인 테스트','USER',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                    id, "approval-" + id + "@example.test", "test-only-not-a-real-hash");
            initialization.initializeForNewMember(id);
        });
        return id;
    }
    private WalletTopupResponse confirm(long owner, WalletTopupResponse topup, String key) {
        return approvals.confirm(owner, Long.parseLong(topup.id()), key, topup.tossOrderId(), topup.amount());
    }
    private Payment done(WalletTopupResponse topup, String key) {
        return new Payment(key, topup.tossOrderId(), topup.amount(), topup.amount(), "KRW", "DONE", OffsetDateTime.now(ZoneOffset.UTC));
    }
    private long balance(long owner) { return jdbc.queryForObject("SELECT balance FROM wallets WHERE member_id=?", Long.class, owner); }
    private long credits(WalletTopupResponse topup) { return jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entries WHERE topup_id=?", Long.class, topup.id()); }
}
