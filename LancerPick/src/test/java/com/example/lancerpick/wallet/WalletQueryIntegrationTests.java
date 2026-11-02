package com.example.lancerpick.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.wallet.entity.WalletEntryType;
import com.example.lancerpick.wallet.service.WalletInitializationService;
import com.example.lancerpick.wallet.service.WalletQueryService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** 내부 읽기 서비스·응답 계약 검사. 인증 주체 검증과 실제 주문/결제 쓰기 기능은 별도다. */
@IntegrationTest
class WalletQueryIntegrationTests {
    private static final AtomicLong IDS = new AtomicLong(180_000);
    private static final LocalDateTime ENTRY_AT = LocalDateTime.parse("2030-01-02T03:04:05.123456");
    private final List<Long> members = new ArrayList<>();
    private final List<Long> categories = new ArrayList<>();
    private final List<Long> products = new ArrayList<>();
    private final List<Long> orders = new ArrayList<>();

    @Autowired WalletQueryService query;
    @Autowired WalletInitializationService initialization;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JsonMapper mapper;

    @AfterEach
    void cleanupOwnFixtures() {
        for (long id : members) jdbc.update("DELETE FROM wallet_entries WHERE member_id = ?", id);
        for (long id : orders) {
            jdbc.update("DELETE FROM payments WHERE order_id = ?", id);
            jdbc.update("DELETE FROM order_snapshots WHERE order_id = ?", id);
            jdbc.update("DELETE FROM orders WHERE id = ?", id);
        }
        for (long id : products) jdbc.update("DELETE FROM products WHERE id = ?", id);
        for (long id : categories) jdbc.update("DELETE FROM categories WHERE id = ?", id);
        for (long id : members) jdbc.update("DELETE FROM wallets WHERE member_id = ?", id);
        for (long id : members) jdbc.update("DELETE FROM members WHERE id = ?", id);
    }

    @Test
    void balanceHasDocumentedTestCurrencyAndBooleanJsonField() {
        long member = memberWithWallet();
        var balance = query.getBalance(member);
        assertThat(balance.balance()).isEqualTo(1_000_000L);
        assertThat(balance.currency()).isEqualTo("KRW");
        assertThat(balance.isTest()).isTrue();
        assertThat(mapper.writeValueAsString(balance)).contains("\"isTest\":true", "\"balance\":1000000");
    }

    @Test
    void initialGrantHasStringIdNullOrderAndUtcTimeWithoutInternalFields() {
        long member = memberWithWallet();
        jdbc.update("UPDATE wallet_entries SET created_at = ? WHERE member_id = ?", ENTRY_AT, member);
        var page = query.getEntries(member, 0, 20);
        assertThat(page.items()).hasSize(1);
        var entry = page.items().getFirst();
        assertThat(entry.id()).matches("[0-9]+");
        assertThat(entry.type()).isEqualTo(WalletEntryType.INITIAL_GRANT);
        assertThat(entry.amountDelta()).isEqualTo(1_000_000L);
        assertThat(entry.balanceAfter()).isEqualTo(1_000_000L);
        assertThat(entry.orderId()).isNull();
        assertThat(entry.createdAt()).isEqualTo(ENTRY_AT.atOffset(ZoneOffset.UTC));
        String json = mapper.writeValueAsString(entry);
        assertThat(json).contains("\"id\":\"", "\"orderId\":null", "2030-01-02T03:04:05.123456Z")
                .doesNotContain("memberId", "paymentId", "eventKey");
    }

    @Test
    void paymentAndRefundResolveOrderThroughPaymentWithoutPaymentEntity() {
        long member = memberWithWallet();
        long order = paymentFixture(member, 50_000, true, ENTRY_AT);
        var result = query.getEntries(member, 0, 20);
        var linked = result.items().stream().filter(e -> e.orderId() != null).toList();
        assertThat(linked).hasSize(2);
        assertThat(linked).allSatisfy(e -> assertThat(e.orderId()).isEqualTo(Long.toString(order)));
        assertThat(linked).extracting(e -> e.type()).containsExactly(WalletEntryType.REFUND, WalletEntryType.PAYMENT);
        assertThat(linked.getFirst().amountDelta()).isEqualTo(50_000L);
        assertThat(linked.get(1).amountDelta()).isEqualTo(-50_000L);
    }

    @Test
    void nativeTimestampRemainsUtcAcrossJvmDefaultTimezones() {
        long member = memberWithWallet();
        jdbc.update("UPDATE wallet_entries SET created_at = ? WHERE member_id = ?", ENTRY_AT, member);
        TimeZone previous = TimeZone.getDefault();
        try {
            for (String zone : List.of("UTC", "Asia/Seoul", "America/Los_Angeles")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                assertThat(query.getEntries(member, 0, 20).items().getFirst().createdAt())
                        .as("JVM timezone %s", zone).isEqualTo(ENTRY_AT.atOffset(ZoneOffset.UTC));
            }
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void onlyRequestedMembersRowsAndCountAreReturned() {
        long first = memberWithWallet();
        long second = memberWithWallet();
        paymentFixture(second, 100, false, ENTRY_AT);
        var firstPage = query.getEntries(first, 0, 20);
        assertThat(firstPage.totalElements()).isEqualTo(1);
        assertThat(firstPage.items()).hasSize(1).allSatisfy(e -> assertThat(e.orderId()).isNull());
        assertThat(query.getEntries(second, 0, 20).totalElements()).isEqualTo(2);
        assertThat(query.getBalance(first).balance()).isEqualTo(1_000_000L);
        assertThat(query.getBalance(second).balance()).isEqualTo(999_900L);
    }

    @Test
    void paginationUsesCreatedAtThenIdDescendingAndConsistentTotals() {
        long member = memberWithWallet();
        jdbc.update("UPDATE wallet_entries SET created_at = ? WHERE member_id = ?", ENTRY_AT.minusDays(1), member);
        paymentFixture(member, 100, true, ENTRY_AT);
        paymentFixture(member, 200, false, ENTRY_AT.plusSeconds(1));
        var expected = jdbc.queryForList("SELECT id FROM wallet_entries WHERE member_id = ? ORDER BY created_at DESC, id DESC", Long.class, member)
                .stream().map(Object::toString).toList();

        var first = query.getEntries(member, 0, 2);
        var second = query.getEntries(member, 1, 2);
        assertThat(first.totalElements()).isEqualTo(4);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(second.totalElements()).isEqualTo(4);
        List<String> actual = new ArrayList<>();
        first.items().forEach(e -> actual.add(e.id()));
        second.items().forEach(e -> actual.add(e.id()));
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    void beyondLastPageIncludingHugeOffsetReturnsEmptyItemsAndKeepsTotals() {
        long member = memberWithWallet();
        var page = query.getEntries(member, Integer.MAX_VALUE, 100);
        assertThat(page.items()).isEmpty();
        assertThat(page.page()).isEqualTo(Integer.MAX_VALUE);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    void emptyHistoryDoesNotCreateEntriesOrGrantMoney() {
        long member = member();
        jdbc.update("INSERT INTO wallets(member_id,balance,updated_at) VALUES (?,0,?)", member, ENTRY_AT);
        var page = query.getEntries(member, 0, 20);
        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
        assertThat(query.getBalance(member).balance()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entries WHERE member_id = ?", Long.class, member)).isZero();
    }

    @Test
    void invalidPaginationAndMemberIdAreRejectedBeforeQuery() {
        long member = memberWithWallet();
        assertCode(() -> query.getEntries(member, -1, 20), "VALIDATION_FAILED");
        assertCode(() -> query.getEntries(member, 0, 0), "VALIDATION_FAILED");
        assertCode(() -> query.getEntries(member, 0, 101), "VALIDATION_FAILED");
        assertCode(() -> query.getEntries(0, 0, 20), "VALIDATION_FAILED");
        assertCode(() -> query.getBalance(-1), "VALIDATION_FAILED");
        assertThat(query.getEntries(member, 0, 100).items()).hasSize(1);
    }

    @Test
    void missingWalletReturnsNotFoundWithoutInitialization() {
        long member = member();
        assertCode(() -> query.getBalance(member), "RESOURCE_NOT_FOUND");
        assertCode(() -> query.getEntries(member, 0, 20), "RESOURCE_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallets WHERE member_id = ?", Long.class, member)).isZero();
    }

    private void assertCode(Runnable operation, String code) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo(code));
    }

    private long member() {
        long id = IDS.incrementAndGet();
        members.add(id);
        jdbc.update("INSERT INTO members(id,email,password_hash,display_name,role,created_at,updated_at) VALUES (?,?,?,'조회 테스트','USER',?,?)",
                id, "wallet-query-" + id + "@example.test", "test-only-hash", LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0));
        return id;
    }

    private long memberWithWallet() {
        long id = member();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> initialization.initializeForNewMember(id));
        return id;
    }

    /** 주문·결제의 실제 쓰기 서비스를 대신하지 않는 V1 관계 조회용 SQL fixture. */
    private long paymentFixture(long buyer, long amount, boolean refunded, LocalDateTime at) {
        long seller = member();
        long category = IDS.incrementAndGet();
        long product = IDS.incrementAndGet();
        // JavaScript의 안전 정수보다 큰 BIGINT도 API에서는 문자열로 보존돼야 한다.
        long order = 9_007_199_254_740_993L + IDS.incrementAndGet();
        long payment = IDS.incrementAndGet();
        categories.add(category);
        products.add(product);
        orders.add(order);
        jdbc.update("INSERT INTO categories(id,name,display_order) VALUES (?, ?, 0)", category, "테스트 분류 " + category);
        jdbc.update("INSERT INTO products(id,seller_id,category_id,title,description,scope_description,preparation_description,price,duration_days,created_at,updated_at) VALUES (?,?,?,'조회용 상품','설명','범위','준비',?,1,?,?)",
                product, seller, category, amount, at.minusDays(2), at.minusDays(2));
        jdbc.update("INSERT INTO orders(id,buyer_id,seller_id,product_id,request_key,status,price_amount,duration_days_snapshot,accepted_at,buyer_confirmed_terms_at,paid_at,due_at,cancelled_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,1,?,?,?,?,?,?,?)",
                order, buyer, seller, product, UUID.randomUUID().toString(), refunded ? "CANCELLED" : "IN_PROGRESS",
                amount, at.minusMinutes(2), at.minusMinutes(1), at.minusMinutes(1), at.plusDays(1).minusMinutes(1),
                refunded ? at : null, at.minusDays(1), at);
        jdbc.update("INSERT INTO order_snapshots(order_id,product_version_snapshot,product_title_snapshot,description_snapshot,scope_snapshot,preparation_snapshot,terms_snapshot,requirements) VALUES (?,0,'조회용 상품','설명','범위','준비','규칙','요구사항')", order);
        jdbc.update("INSERT INTO payments(id,order_id,amount,status,paid_at,refunded_at) VALUES (?,?,?,?,?,?)",
                payment, order, amount, refunded ? "REFUNDED" : "PAID", at.minusMinutes(1), refunded ? at : null);
        long balance = jdbc.queryForObject("SELECT balance FROM wallets WHERE member_id = ?", Long.class, buyer);
        jdbc.update("INSERT INTO wallet_entries(member_id,payment_id,type,amount_delta,balance_after,event_key,created_at) VALUES (?,?,'PAYMENT',?,?,?,?)",
                buyer, payment, -amount, balance - amount, "PAYMENT:" + payment, at);
        if (refunded) {
            jdbc.update("INSERT INTO wallet_entries(member_id,payment_id,type,amount_delta,balance_after,event_key,created_at) VALUES (?,?,'REFUND',?,?,?,?)",
                    buyer, payment, amount, balance, "REFUND:" + payment, at);
        }
        jdbc.update("UPDATE wallets SET balance = ?, updated_at = ? WHERE member_id = ?", refunded ? balance : balance - amount, at, buyer);
        return order;
    }
}
