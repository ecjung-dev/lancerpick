package com.example.lancerpick.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.wallet.entity.WalletTopup;
import com.example.lancerpick.wallet.entity.WalletTopupStatus;
import com.example.lancerpick.wallet.entity.WalletEntryType;
import com.example.lancerpick.wallet.repository.WalletTopupRepository;
import com.example.lancerpick.wallet.service.WalletInitializationService;
import com.example.lancerpick.wallet.service.WalletQueryService;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 실제 MySQL V2 제약·JPA 매핑·기존 조회 호환 검사. 토스 승인 서비스 테스트는 아니다. */
@IntegrationTest
@Transactional
class WalletTopupSchemaIntegrationTests {
    private static final AtomicLong IDS = new AtomicLong(650_000);
    private static final LocalDateTime NOW = LocalDateTime.parse("2030-01-02T03:04:05.123456");
    // JdbcTemplate은 Hibernate의 hibernate.jdbc.time_zone 설정을 사용하지 않는다.
    private static final Timestamp SQL_NOW = Timestamp.from(NOW.toInstant(ZoneOffset.UTC));
    @Autowired JdbcTemplate jdbc;
    @Autowired WalletTopupRepository topups;
    @Autowired WalletInitializationService initialization;
    @Autowired WalletQueryService query;
    @Autowired EntityManager entityManager;

    @Test
    void readyRequestPersistsWithoutGrantingCreditsAndIsScopedToOwner() {
        long owner = member();
        long other = member();
        var saved = request(owner, 50000);
        entityManager.clear();
        var found = topups.findOwnedByIdForUpdate(saved.getId(), owner).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(WalletTopupStatus.READY);
        assertThat(found.getCurrency()).isEqualTo("KRW");
        assertThat(found.isTest()).isTrue();
        assertThat(found.getPaymentKey()).isNull();
        assertThat(found.getCreatedAt()).isEqualTo(NOW.toInstant(ZoneOffset.UTC));
        assertThat(found.getUpdatedAt()).isEqualTo(NOW.toInstant(ZoneOffset.UTC));
        assertThat(found.getProcessingStartedAt()).isNull();
        assertThat(found.getApprovedAt()).isNull();
        assertThat(found.getCompletedAt()).isNull();
        assertThat(found.getLastErrorCode()).isNull();
        assertThat(topups.findByIdAndMemberId(saved.getId(), other)).isEmpty();
        assertThat(topups.findOwnedByIdForUpdate(saved.getId(), other)).isEmpty();
        assertThat(query.getBalance(owner).balance()).isEqualTo(1000000);
        assertThat(query.getEntries(owner, 0, 20).totalElements()).isEqualTo(1);
    }

    @Test
    void duplicateTossOrderAndApprovalKeyAreRejected() {
        var saved = request(member(), 50000);
        assertThatThrownBy(() -> jdbc.update("UPDATE wallet_topups SET toss_order_id = ? WHERE id = ?",
                saved.getTossOrderId(), request(saved.getMemberId(), 10000).getId()))
                .isInstanceOf(DataAccessException.class).satisfies(WalletTopupSchemaIntegrationTests::isConstraintViolation);
        var another = request(saved.getMemberId(), 10000);
        assertThatThrownBy(() -> jdbc.update("UPDATE wallet_topups SET approval_request_key = ? WHERE id = ?",
                saved.getApprovalRequestKey(), another.getId())).isInstanceOf(DataAccessException.class).satisfies(WalletTopupSchemaIntegrationTests::isConstraintViolation);
    }

    @Test
    void paymentKeysAreUniqueButMultipleUnboundRequestsAreAllowed() {
        long owner = member();
        var first = request(owner, 50000);
        var second = request(owner, 50000);
        assertThat(first.getPaymentKey()).isNull();
        assertThat(second.getPaymentKey()).isNull();
        processing(first.getId(), "topup-test-payment-key");
        assertThatThrownBy(() -> processing(second.getId(), "topup-test-payment-key"))
                .isInstanceOf(DataAccessException.class).satisfies(WalletTopupSchemaIntegrationTests::isConstraintViolation);
    }

    @ParameterizedTest
    @CsvSource({"amount,0", "amount,-1", "amount,1000001", "is_test,0"})
    void invalidAmountsAndLiveFlagAreRejected(String column, long value) {
        var topup = request(member(), 50000);
        // column은 테스트의 고정 목록에서만 온다. 실제 API에서 동적 SQL을 만들지 않는다.
        assertThatThrownBy(() -> jdbc.update("UPDATE wallet_topups SET " + column + " = ? WHERE id = ?",
                value, topup.getId())).isInstanceOf(DataAccessException.class).satisfies(WalletTopupSchemaIntegrationTests::isConstraintViolation);
    }

    @Test
    void currencyAndIdentifierFormatsAreCheckedInDatabase() {
        var topup = request(member(), 50000);
        assertThatThrownBy(() -> jdbc.update("UPDATE wallet_topups SET currency = 'USD' WHERE id = ?", topup.getId()))
                .isInstanceOf(DataAccessException.class).satisfies(WalletTopupSchemaIntegrationTests::isConstraintViolation);
        assertThatThrownBy(() -> jdbc.update("UPDATE wallet_topups SET toss_order_id = 'bad id' WHERE id = ?", topup.getId()))
                .isInstanceOf(DataAccessException.class).satisfies(WalletTopupSchemaIntegrationTests::isConstraintViolation);
        assertThatThrownBy(() -> jdbc.update("UPDATE wallet_topups SET approval_request_key = 'bad' WHERE id = ?", topup.getId()))
                .isInstanceOf(DataAccessException.class).satisfies(WalletTopupSchemaIntegrationTests::isConstraintViolation);
    }

    @Test
    void unknownNeedsProcessingEvidenceAndCompletionNeedsApprovalTimes() {
        var topup = request(member(), 50000);
        assertThatThrownBy(() -> jdbc.update("UPDATE wallet_topups SET status = 'UNKNOWN' WHERE id = ?", topup.getId()))
                .isInstanceOf(DataAccessException.class).satisfies(WalletTopupSchemaIntegrationTests::isConstraintViolation);
        processing(topup.getId(), "test-unknown-payment");
        jdbc.update("UPDATE wallet_topups SET status = 'UNKNOWN' WHERE id = ?", topup.getId());
        assertThatThrownBy(() -> jdbc.update("UPDATE wallet_topups SET status = 'COMPLETED' WHERE id = ?", topup.getId()))
                .isInstanceOf(DataAccessException.class).satisfies(WalletTopupSchemaIntegrationTests::isConstraintViolation);
        assertThatThrownBy(() -> jdbc.update("UPDATE wallet_topups SET status = 'UNSUPPORTED' WHERE id = ?", topup.getId()))
                .isInstanceOf(DataAccessException.class).satisfies(WalletTopupSchemaIntegrationTests::isConstraintViolation);
    }

    @Test
    void timeOrderingAndMissingWalletAreRejected() {
        var topup = request(member(), 50000);
        assertThatThrownBy(() -> jdbc.update("UPDATE wallet_topups SET updated_at = ? WHERE id = ?", Timestamp.from(NOW.minusDays(1).toInstant(ZoneOffset.UTC)), topup.getId()))
                .isInstanceOf(DataAccessException.class).satisfies(WalletTopupSchemaIntegrationTests::isConstraintViolation);
        assertThatThrownBy(() -> jdbc.update("UPDATE wallet_topups SET member_id = ? WHERE id = ?", Long.MAX_VALUE, topup.getId()))
                .isInstanceOf(DataAccessException.class).satisfies(WalletTopupSchemaIntegrationTests::isConstraintViolation);
    }

    @Test
    void topupLedgerMustReferenceSameOwnerAndExactAmountAndOnlyOnce() {
        long owner = member();
        long other = member();
        var topup = request(owner, 50000);
        assertThatThrownBy(() -> creditFixture(topup.getId(), other, 50000))
                .isInstanceOf(DataAccessException.class).satisfies(WalletTopupSchemaIntegrationTests::isConstraintViolation);
        assertThatThrownBy(() -> creditFixture(topup.getId(), owner, 49999))
                .isInstanceOf(DataAccessException.class).satisfies(WalletTopupSchemaIntegrationTests::isConstraintViolation);
        creditFixture(topup.getId(), owner, 50000);
        assertThatThrownBy(() -> creditFixture(topup.getId(), owner, 50000))
                .isInstanceOf(DataAccessException.class).satisfies(WalletTopupSchemaIntegrationTests::isConstraintViolation);
    }

    @Test
    void nonTopupEntryCannotHaveTopupReferenceAndTopupRequiresReference() {
        long owner = member();
        var topup = request(owner, 50000);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO wallet_entries(member_id,topup_id,type,amount_delta,balance_after,event_key,created_at)
                VALUES (?,?,'INITIAL_GRANT',1000000,1000000,?,?)
                """, owner, topup.getId(), UUID.randomUUID().toString(), SQL_NOW))
                .isInstanceOf(DataAccessException.class).satisfies(WalletTopupSchemaIntegrationTests::isConstraintViolation);
        assertThatThrownBy(() -> creditFixture(null, owner, 50000))
                .isInstanceOf(DataAccessException.class).satisfies(WalletTopupSchemaIntegrationTests::isConstraintViolation);
    }

    @Test
    void topupLedgerIsReadableWithoutChangingExistingResponseShape() {
        long owner = member();
        var topup = request(owner, 50000);
        processing(topup.getId(), "test-completed-payment");
        creditFixture(topup.getId(), owner, 50000);
        jdbc.update("UPDATE wallets SET balance = 1050000, updated_at = ? WHERE member_id = ?", SQL_NOW, owner);
        jdbc.update("UPDATE wallet_topups SET status='COMPLETED', approved_at=?, completed_at=? WHERE id=?", SQL_NOW, SQL_NOW, topup.getId());
        var entry = query.getEntries(owner, 0, 20).items().stream()
                .filter(item -> item.type() == WalletEntryType.TOPUP).findFirst().orElseThrow();
        assertThat(entry.amountDelta()).isEqualTo(50000);
        assertThat(entry.balanceAfter()).isEqualTo(1050000);
        assertThat(entry.orderId()).isNull();
        assertThat(query.getBalance(owner).balance()).isEqualTo(1050000);
    }

    private long member() {
        long id = IDS.incrementAndGet();
        jdbc.update("INSERT INTO members(id,email,password_hash,display_name,role,created_at,updated_at) VALUES (?,?,?,'충전 테스트','USER',?,?)",
                id, "topup-" + id + "@example.test", "test-only-not-a-real-hash", SQL_NOW, SQL_NOW);
        initialization.initializeForNewMember(id);
        return id;
    }

    private WalletTopup request(long memberId, long amount) {
        return topups.saveAndFlush(WalletTopup.request(memberId, "topup_" + UUID.randomUUID(), amount, UUID.randomUUID(), NOW.toInstant(ZoneOffset.UTC)));
    }

    private void processing(long id, String paymentKey) {
        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f') FROM wallet_topups WHERE id=?", String.class, id))
                .isEqualTo("2030-01-02 03:04:05.123456");
        jdbc.update("UPDATE wallet_topups SET status='PROCESSING', payment_key=?, processing_started_at=? WHERE id=?", paymentKey, SQL_NOW, id);
    }

    private void creditFixture(Long topupId, long memberId, long amount) {
        jdbc.update("""
                INSERT INTO wallet_entries(member_id,topup_id,type,amount_delta,balance_after,event_key,created_at)
                VALUES (?,?,'TOPUP',?,1050000,?,?)
                """, memberId, topupId, amount, "TOPUP-TEST:" + UUID.randomUUID(), SQL_NOW);
    }

    private static void isConstraintViolation(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        assertThat(root).isInstanceOfSatisfying(SQLException.class,
                sql -> assertThat(sql.getErrorCode()).isIn(1062, 1452, 3819));
    }
}
