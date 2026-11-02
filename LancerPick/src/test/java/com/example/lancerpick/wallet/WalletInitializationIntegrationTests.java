package com.example.lancerpick.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.wallet.entity.WalletEntryType;
import com.example.lancerpick.wallet.repository.WalletEntryRepository;
import com.example.lancerpick.wallet.repository.WalletRepository;
import com.example.lancerpick.wallet.service.WalletInitializationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 별도 MySQL의 실제 V1·JPA·잠금을 검증한다. 테스트 자체에는 자동 롤백 트랜잭션을 붙이지 않는다. */
@IntegrationTest
@Import(WalletInitializationIntegrationTests.FixedClockConfig.class)
class WalletInitializationIntegrationTests {
    private static final AtomicLong IDS = new AtomicLong(80_000);
    private static final LocalDateTime GRANTED_AT = LocalDateTime.parse("2026-11-02T03:04:05.123456");
    private final Set<Long> fixtureMembers = ConcurrentHashMap.newKeySet();

    @Autowired WalletInitializationService service;
    @Autowired WalletRepository wallets;
    @Autowired WalletEntryRepository entries;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @AfterEach
    void removeOnlyThisTestsFixtures() {
        for (long id : fixtureMembers) jdbc.update("DELETE FROM wallet_entries WHERE member_id = ?", id);
        for (long id : fixtureMembers) jdbc.update("DELETE FROM wallets WHERE member_id = ?", id);
        for (long id : fixtureMembers) jdbc.update("DELETE FROM members WHERE id = ?", id);
    }

    @Test
    void signupTransactionCreatesWalletAndOneGrantWithMicrosecondUtcTime() {
        long id = nextId();
        inTransaction(() -> {
            insertMember(id);
            service.initializeForNewMember(id);
        });

        var wallet = wallets.findById(id).orElseThrow();
        var grant = entries.findAll().stream().filter(e -> e.getMemberId() == id).findFirst().orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(1_000_000L);
        assertThat(wallet.getUpdatedAt()).isEqualTo(GRANTED_AT);
        assertThat(grant.getType()).isEqualTo(WalletEntryType.INITIAL_GRANT);
        assertThat(grant.getPaymentId()).isNull();
        assertThat(grant.getAmountDelta()).isEqualTo(1_000_000L);
        assertThat(grant.getBalanceAfter()).isEqualTo(1_000_000L);
        assertThat(grant.getEventKey()).isEqualTo("INITIAL_GRANT:" + id);
        assertThat(grant.getCreatedAt()).isEqualTo(GRANTED_AT);
        assertCounts(id, 1, 1, 1);
    }

    @Test
    void refusesStandaloneGrantWithoutSignupTransaction() {
        long id = createMember();
        assertThatThrownBy(() -> service.initializeForNewMember(id))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertCounts(id, 1, 0, 0);
    }

    @Test
    void repeatedCallPreservesBalanceTimestampAndOriginalEntry() {
        long id = createMember();
        inTransaction(() -> service.initializeForNewMember(id));
        Long originalEntry = jdbc.queryForObject("SELECT id FROM wallet_entries WHERE member_id = ?", Long.class, id);

        inTransaction(() -> service.initializeForNewMember(id));

        assertCounts(id, 1, 1, 1);
        assertThat(jdbc.queryForObject("SELECT balance FROM wallets WHERE member_id = ?", Long.class, id)).isEqualTo(1_000_000L);
        assertThat(jdbc.queryForObject("SELECT id FROM wallet_entries WHERE member_id = ?", Long.class, id)).isEqualTo(originalEntry);
        assertThat(wallets.findById(id).orElseThrow().getUpdatedAt()).isEqualTo(GRANTED_AT);
    }

    @Test
    void repeatedInitializationDoesNotResetAnExistingLowerBalance() {
        long id = createMember();
        inTransaction(() -> service.initializeForNewMember(id));
        // 지급 재호출이 현재 잔액을 덮어쓰지 않는지 분리해서 보는 DB fixture. 결제 기능 테스트는 아니다.
        jdbc.update("UPDATE wallets SET balance = 1234 WHERE member_id = ?", id);
        inTransaction(() -> service.initializeForNewMember(id));
        assertThat(wallets.findById(id).orElseThrow().getBalance()).isEqualTo(1234L);
        assertCounts(id, 1, 1, 1);
    }

    @RepeatedTest(5)
    void simultaneousFirstCallsForOneMemberOnlyGrantOnce() throws Exception {
        long id = createMember();
        concurrent(4, ignored -> inTransaction(() -> service.initializeForNewMember(id)));
        assertCounts(id, 1, 1, 1);
        assertThat(wallets.findById(id).orElseThrow().getBalance()).isEqualTo(1_000_000L);
        assertThat(jdbc.queryForObject("SELECT SUM(amount_delta) FROM wallet_entries WHERE member_id = ?", Long.class, id))
                .isEqualTo(1_000_000L);
    }

    @RepeatedTest(5)
    void differentMembersCanInitializeConcurrently() throws Exception {
        long first = createMember();
        long second = createMember();
        concurrent(4, index -> inTransaction(() -> service.initializeForNewMember(index % 2 == 0 ? first : second)));
        assertCounts(first, 1, 1, 1);
        assertCounts(second, 1, 1, 1);
    }

    @Test
    void oldRepeatableReadSnapshotStillRecognizesGrantCommittedByAnotherTransaction() throws Exception {
        assertThat(jdbc.queryForObject("SELECT @@transaction_isolation", String.class)).isEqualTo("REPEATABLE-READ");
        long id = createMember();
        var executor = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                // 일반 조회로 스냅샷을 먼저 만든 뒤 다른 트랜잭션이 최초 지급을 커밋한다.
                jdbc.queryForObject("SELECT COUNT(*) FROM wallets WHERE member_id = ?", Long.class, id);
                try {
                    executor.submit(() -> inTransaction(() -> service.initializeForNewMember(id))).get(15, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                service.initializeForNewMember(id);
            });
        } finally {
            executor.shutdownNow();
        }
        assertCounts(id, 1, 1, 1);
    }

    @Test
    void entryUniqueConstraintFailureRollsBackNewMemberAndWallet() {
        long targetId = nextId();
        long otherId = createMember();
        jdbc.update("INSERT INTO wallets(member_id,balance,updated_at) VALUES (?,1000000,?)", otherId, GRANTED_AT);
        // 다른 지갑에 목표 이벤트 키를 선점한 비정상 fixture로 원장 INSERT 실패를 실제 DB에서 만든다.
        jdbc.update("INSERT INTO wallet_entries(member_id,type,amount_delta,balance_after,event_key,created_at) VALUES (?,'INITIAL_GRANT',1000000,1000000,?,?)",
                otherId, "INITIAL_GRANT:" + targetId, GRANTED_AT);

        assertThatThrownBy(() -> inTransaction(() -> {
            insertMember(targetId);
            service.initializeForNewMember(targetId);
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertCounts(targetId, 0, 0, 0);
        assertCounts(otherId, 1, 1, 1);
    }

    @Test
    void laterSignupFailureRollsBackMemberWalletAndEntryTogether() {
        long id = nextId();
        assertThatThrownBy(() -> inTransaction(() -> {
            insertMember(id);
            service.initializeForNewMember(id);
            throw new IllegalStateException("simulated signup failure");
        })).isInstanceOf(IllegalStateException.class);
        assertCounts(id, 0, 0, 0);
    }

    @Test
    void existingWalletWithoutGrantFailsWithoutAutomaticRepair() {
        long id = createMember();
        jdbc.update("INSERT INTO wallets(member_id,balance,updated_at) VALUES (?,0,?)", id, GRANTED_AT);
        assertThatThrownBy(() -> inTransaction(() -> service.initializeForNewMember(id)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("DATA_INTEGRITY_ERROR"));
        assertCounts(id, 1, 1, 0);
        assertThat(wallets.findById(id).orElseThrow().getBalance()).isZero();
    }

    @Test
    void refusesGrantRecordOwnedByAnotherMember() {
        long first = createMember();
        long second = createMember();
        jdbc.update("INSERT INTO wallets(member_id,balance,updated_at) VALUES (?,0,?), (?,1000000,?)", first, GRANTED_AT, second, GRANTED_AT);
        jdbc.update("INSERT INTO wallet_entries(member_id,type,amount_delta,balance_after,event_key,created_at) VALUES (?,'INITIAL_GRANT',1000000,1000000,?,?)",
                second, "INITIAL_GRANT:" + first, GRANTED_AT);
        assertThatThrownBy(() -> inTransaction(() -> service.initializeForNewMember(first)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("DATA_INTEGRITY_ERROR"));
        assertCounts(first, 1, 1, 0);
        assertCounts(second, 1, 1, 1);
    }

    @Test
    void rejectsMissingAndInvalidMemberWithoutCreatingMoney() {
        long missing = nextId();
        assertThatThrownBy(() -> inTransaction(() -> service.initializeForNewMember(missing)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("RESOURCE_NOT_FOUND"));
        assertThatThrownBy(() -> inTransaction(() -> service.initializeForNewMember(0)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("VALIDATION_FAILED"));
        assertCounts(missing, 0, 0, 0);
    }

    private long nextId() {
        long id = IDS.incrementAndGet();
        fixtureMembers.add(id);
        return id;
    }

    private long createMember() {
        long id = nextId();
        insertMember(id);
        return id;
    }

    private void insertMember(long id) {
        jdbc.update("INSERT INTO members(id,email,password_hash,display_name,role,created_at,updated_at) VALUES (?,?,?,'지갑 테스트','USER',?,?)",
                id, "wallet-test-" + id + "@example.test", "test-only-not-a-real-hash", GRANTED_AT, GRANTED_AT);
    }

    private void inTransaction(Runnable task) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> task.run());
    }

    private void assertCounts(long id, long memberCount, long walletCount, long entryCount) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM members WHERE id = ?", Long.class, id)).isEqualTo(memberCount);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallets WHERE member_id = ?", Long.class, id)).isEqualTo(walletCount);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entries WHERE member_id = ?", Long.class, id)).isEqualTo(entryCount);
    }

    private void concurrent(int workers, IntConsumer task) throws Exception {
        var executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start barrier timeout");
                    task.accept(index);
                    return null;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) future.get(30, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-11-02T03:04:05.123456789Z"), ZoneOffset.UTC);
        }
    }
}
