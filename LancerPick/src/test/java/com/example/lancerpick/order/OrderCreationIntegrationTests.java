package com.example.lancerpick.order;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.order.dto.CreateOrderRequest;
import com.example.lancerpick.order.repository.OrderCreationQueries;
import com.example.lancerpick.order.repository.OrderHistoryRepository;
import com.example.lancerpick.order.repository.OrderSnapshotRepository;
import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.order.service.NewOrderPolicy;
import com.example.lancerpick.order.service.OrderCreationService;
import com.example.lancerpick.order.service.OrderTerms;
import com.example.lancerpick.support.IntegrationTest;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;

/** 실제 서비스 트랜잭션을 MySQL로 검증한다. HTTP 인증은 별도 연결 대상이다. */
@IntegrationTest
@Import(OrderCreationIntegrationTests.FixedClockConfig.class)
class OrderCreationIntegrationTests {
    private static final LocalDateTime FIXTURE_TIME = LocalDateTime.parse("2026-11-02T06:00:00.123456");
    @Autowired OrderCreationService service;
    @Autowired OrderSnapshotRepository snapshots;
    @MockitoSpyBean OrderHistoryRepository histories;
    @MockitoSpyBean OrderCreationQueries source;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean OrderTerms terms;
    @MockitoSpyBean NewOrderPolicy newOrderPolicy;

    private final List<Long> members = new ArrayList<>();
    private final List<Long> extraProducts = new ArrayList<>();
    private long buyerId;
    private long sellerId;
    private long categoryId;
    private long productId;

    @BeforeEach
    void fixtures() {
        buyerId = member();
        sellerId = member();
        categoryId = insert("INSERT INTO categories(name, display_order) VALUES (?, 0)",
                "creation-" + UUID.randomUUID());
        productId = insert("""
                INSERT INTO products(seller_id, category_id, title, description, scope_description,
                    preparation_description, price, duration_days, created_at, updated_at)
                VALUES (?, ?, 'title', 'description', 'scope', 'preparation', 100000, 3, ?, ?)
                """, sellerId, categoryId, FIXTURE_TIME, FIXTURE_TIME);
    }

    @AfterEach
    void cleanup() {
        reset(terms);
        reset(histories);
        reset(source);
        reset(newOrderPolicy);
        var products = new ArrayList<>(extraProducts);
        if (productId != 0) products.add(productId);
        for (long id : products) {
            jdbc.update("DELETE FROM order_histories WHERE order_id IN (SELECT id FROM orders WHERE product_id = ?)", id);
            jdbc.update("DELETE FROM order_trade_terms WHERE order_id IN (SELECT id FROM orders WHERE product_id = ?)", id);
            jdbc.update("DELETE FROM order_snapshots WHERE order_id IN (SELECT id FROM orders WHERE product_id = ?)", id);
            jdbc.update("DELETE FROM orders WHERE product_id = ?", id);
            jdbc.update("DELETE FROM products WHERE id = ?", id);
        }
        if (categoryId != 0) jdbc.update("DELETE FROM categories WHERE id = ?", categoryId);
        for (long id : members) jdbc.update("DELETE FROM members WHERE id = ?", id);
    }

    @Test
    void createsThreeRecordsWithServerConditionsAndSharedUtcTime() {
        var result = service.create(buyerId, request(UUID.randomUUID(), "\u3000요구사항\r\n다음 줄 "));
        assertThat(result.created()).isTrue();
        assertCounts(1);
        var row = jdbc.queryForMap("SELECT * FROM orders WHERE id = ?", result.orderId());
        assertThat(((Number) row.get("buyer_id")).longValue()).isEqualTo(buyerId);
        assertThat(((Number) row.get("seller_id")).longValue()).isEqualTo(sellerId);
        assertThat(((Number) row.get("price_amount")).longValue()).isEqualTo(100000L);
        assertThat(((Number) row.get("duration_days_snapshot")).intValue()).isEqualTo(3);
        assertThat(row.get("status")).isEqualTo("REQUESTED");
        assertThat(row.get("paid_at")).isNull();
        var snapshot = snapshots.findById(result.orderId()).orElseThrow();
        assertThat(snapshot.getProductTitleSnapshot()).isEqualTo("title");
        assertThat(snapshot.getDescriptionSnapshot()).isEqualTo("description");
        assertThat(snapshot.getScopeSnapshot()).isEqualTo("scope");
        assertThat(snapshot.getPreparationSnapshot()).isEqualTo("preparation");
        assertThat(snapshot.getRequirements()).isEqualTo("요구사항\n다음 줄");
        assertThat(snapshot.getTermsSnapshot()).isEqualTo(terms.currentText().stripTrailing());
        var history = histories.findByOrderIdOrderByCreatedAtAscIdAsc(result.orderId()).getFirst();
        assertThat(history.getActorId()).isEqualTo(buyerId);
        assertThat(history.getCreatedAt().getNano() % 1000).isZero();
        assertThat(history.getCreatedAt()).isEqualTo(FIXTURE_TIME);
        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s.%f') FROM orders WHERE id = ?",
                String.class, result.orderId())).isEqualTo("2026-11-02T06:00:00.123456");
        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s.%f') FROM order_histories WHERE order_id = ?",
                String.class, result.orderId())).isEqualTo("2026-11-02T06:00:00.123456");
    }

    @Test
    void replayUsesOriginalInputBeforeCurrentProductOrOrderStateChecks() {
        UUID key = UUID.randomUUID();
        var first = service.create(buyerId, request(key, "요구사항\n다음 줄"));
        String originalTerms = snapshots.findById(first.orderId()).orElseThrow().getTermsSnapshot();
        doReturn(null).when(terms).currentText();
        jdbc.update("UPDATE products SET is_hidden = true, sale_status = 'STOPPED', version = version + 1, price = 200000 WHERE id = ?", productId);
        jdbc.update("UPDATE orders SET status = 'CANCELLED', cancelled_at = created_at WHERE id = ?", first.orderId());
        var replay = service.create(buyerId, new CreateOrderRequest(Long.toString(productId), 0L,
                "\u00a0요구사항\r\n다음 줄\u3000", key.toString().toUpperCase(java.util.Locale.ROOT)));
        assertThat(replay.created()).isFalse();
        assertThat(replay.orderId()).isEqualTo(first.orderId());
        assertThat(snapshots.findById(first.orderId()).orElseThrow().getTermsSnapshot()).isEqualTo(originalTerms);
        assertCounts(1);
    }

    @Test
    void reusedKeyWithAnyDifferentInputIsRejected() {
        UUID key = UUID.randomUUID();
        service.create(buyerId, request(key, "original"));
        for (var changed : List.of(
                new CreateOrderRequest(Long.toString(productId + 1), 0L, "original", key.toString()),
                new CreateOrderRequest(Long.toString(productId), 1L, "original", key.toString()),
                request(key, "changed"))) {
            assertBusiness(() -> service.create(buyerId, changed), "IDEMPOTENCY_KEY_REUSED");
        }
        assertCounts(1);
    }

    @Test
    void sameInputWithNewKeyCreatesAnotherOrder() {
        var first = service.create(buyerId, request(UUID.randomUUID(), "same"));
        var second = service.create(buyerId, request(UUID.randomUUID(), "same"));
        assertThat(second.orderId()).isNotEqualTo(first.orderId());
        assertCounts(2);
    }

    @Test
    void invalidOrMissingBuyerIsRejected() {
        for (Long id : new Long[]{null, 0L, -1L, Long.MAX_VALUE}) {
            assertBusiness(() -> service.create(id, request(UUID.randomUUID(), "text")), "AUTHENTICATION_REQUIRED");
        }
        assertCounts(0);
    }

    @Test
    void missingOrHiddenProductIsNotDisclosed() {
        assertBusiness(() -> service.create(buyerId,
                new CreateOrderRequest(Long.toString(Long.MAX_VALUE), 0L, "text", UUID.randomUUID().toString())), "RESOURCE_NOT_FOUND");
        jdbc.update("UPDATE products SET is_hidden = true WHERE id = ?", productId);
        assertBusiness(() -> service.create(buyerId, request(UUID.randomUUID(), "text")), "RESOURCE_NOT_FOUND");
        assertCounts(0);
    }

    @Test
    void stoppedProductSelfPurchaseAndChangedVersionAreRejected() {
        assertBusiness(() -> service.create(sellerId, request(UUID.randomUUID(), "text")), "SELF_PURCHASE_NOT_ALLOWED");
        jdbc.update("UPDATE products SET sale_status = 'STOPPED' WHERE id = ?", productId);
        assertBusiness(() -> service.create(buyerId, request(UUID.randomUUID(), "text")), "PRODUCT_NOT_ORDERABLE");
        jdbc.update("UPDATE products SET sale_status = 'ON_SALE', version = 1 WHERE id = ?", productId);
        assertBusiness(() -> service.create(buyerId, request(UUID.randomUUID(), "text")), "PRODUCT_VERSION_CONFLICT");
        assertCounts(0);
    }

    @Test
    void serviceRejectsInvalidRequestWithoutDependingOnControllerValidation() {
        assertBusiness(() -> service.create(buyerId, null), "VALIDATION_FAILED");
        for (Long version : new Long[]{null, -1L}) {
            assertBusiness(() -> service.create(buyerId,
                    new CreateOrderRequest(Long.toString(productId), version, "text", UUID.randomUUID().toString())), "VALIDATION_FAILED");
        }
        assertBusiness(() -> service.create(buyerId, request(UUID.randomUUID(), "\u3000\u00a0")), "VALIDATION_FAILED");
        assertCounts(0);
    }

    @Test
    void snapshotValidationFailureRollsBackAlreadyInsertedOrder() {
        var request = request(UUID.randomUUID(), "text");
        doReturn(null).when(terms).currentText();
        assertBusiness(() -> service.create(buyerId, request), "VALIDATION_FAILED");
        assertCounts(0);
        reset(terms);
        assertThat(service.create(buyerId, request).created()).isTrue();
        assertCounts(1);
    }

    @Test
    void actualHistoryDatabaseFailureRollsBackServiceTransaction() {
        // 이력 저장 경계에서 실제 FK 위반을 유도한다. DB 권한·스키마·서비스 코드는 변경하지 않는다.
        doAnswer(invocation -> {
            var history = invocation.getArgument(0, com.example.lancerpick.order.entity.OrderHistory.class);
            jdbc.update("""
                    INSERT INTO order_histories(order_id, actor_id, action, to_status, created_at)
                    VALUES (?, ?, 'CREATED', 'REQUESTED', ?)
                    """, history.getOrderId(), Long.MAX_VALUE, history.getCreatedAt());
            return history;
        }).when(histories).saveAndFlush(any(com.example.lancerpick.order.entity.OrderHistory.class));
        var request = request(UUID.randomUUID(), "text");
        assertThatThrownBy(() -> service.create(buyerId, request))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .hasStackTraceContaining("fk_order_histories_actor");
        assertCounts(0);
        reset(histories);
        assertThat(service.create(buyerId, request).created()).isTrue();
        assertCounts(1);
    }

    @Test
    void simultaneousSameKeyRequestsCreateExactlyOneTriple() throws Exception {
        var executor = Executors.newFixedThreadPool(4);
        var ready = new CountDownLatch(4);
        var start = new CountDownLatch(1);
        var request = request(UUID.randomUUID(), "text");
        try {
            var futures = new ArrayList<java.util.concurrent.Future<OrderCreationService.Result>>();
            for (int i = 0; i < 4; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                    return service.create(buyerId, request);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results = new ArrayList<OrderCreationService.Result>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            assertThat(results.stream().filter(OrderCreationService.Result::created).count()).isEqualTo(1L);
            assertThat(results.stream().map(OrderCreationService.Result::orderId).distinct().count()).isEqualTo(1L);
            assertCounts(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void incompatibleOuterTransactionIsRejectedBeforeAnyWrite() {
        var request = request(UUID.randomUUID(), "text");
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                service.create(buyerId, request)))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertCounts(0);
    }

    @Test
    void compatibleOuterTransactionRollsBackTheWholeCreation() {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.executeWithoutResult(status -> {
            assertThat(service.create(buyerId, request(UUID.randomUUID(), "text")).created()).isTrue();
            status.setRollbackOnly();
        });
        assertCounts(0);
    }

    @Test
    void differentBuyersCanCreateOrdersConcurrentlyWithSameRequestKey() throws Exception {
        long otherBuyer = member();
        UUID key = UUID.randomUUID();
        var request = request(key, "text");
        List<OrderCreationService.Result> results = runTogether(List.of(() -> service.create(buyerId, request),
                () -> service.create(otherBuyer, request)));
        assertThat(results).allMatch(OrderCreationService.Result::created);
        assertThat(results.stream().map(OrderCreationService.Result::orderId).distinct().count()).isEqualTo(2L);
        assertCounts(2);
    }

    @RepeatedTest(5)
    void twoMembersCanBuyEachOthersProductsConcurrently() throws Exception {
        long buyersProduct = insert("""
                INSERT INTO products(seller_id, category_id, title, description, scope_description,
                    preparation_description, price, duration_days, created_at, updated_at)
                VALUES (?, ?, 'buyer product', 'description', 'scope', 'preparation', 100000, 3, ?, ?)
                """, buyerId, categoryId, FIXTURE_TIME, FIXTURE_TIME);
        extraProducts.add(buyersProduct);
        var bothReadSeller = new java.util.concurrent.CyclicBarrier(2);
        // 두 요청 모두 상대 판매자를 발견한 뒤 잠금 경쟁을 시작한다. 서비스·DB 저장은 실제 코드다.
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            bothReadSeller.await(10, TimeUnit.SECONDS);
            return result;
        }).when(source).findProductSellerId(org.mockito.ArgumentMatchers.anyLong());
        var key = UUID.randomUUID().toString();
        List<OrderCreationService.Result> results = runTogether(List.of(
                () -> service.create(buyerId, new CreateOrderRequest(Long.toString(productId), 0L, "A buys B", key)),
                () -> service.create(sellerId, new CreateOrderRequest(Long.toString(buyersProduct), 0L, "B buys A", key))));

        assertThat(results).allMatch(OrderCreationService.Result::created);
        assertThat(results.stream().map(OrderCreationService.Result::orderId).distinct().count()).isEqualTo(2L);
        for (var result : results) {
            assertThat(snapshots.findById(result.orderId())).isPresent();
            assertThat(histories.findByOrderIdOrderByCreatedAtAscIdAsc(result.orderId())).hasSize(1);
        }
        assertCounts(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE product_id = ?", Long.class, buyersProduct))
                .isEqualTo(1L);
    }

    @Test
    void changedSellerAfterPreReadIsRejectedBeforeAnyInsert() {
        // 판매자 사전 조회가 오래된 경우를 유도해 잠그지 않은 회원을 참조하기 전에 거절하는지 확인한다.
        doReturn(java.util.Optional.of(buyerId)).when(source).findProductSellerId(productId);
        assertBusiness(() -> service.create(buyerId, request(UUID.randomUUID(), "text")), "PRODUCT_VERSION_CONFLICT");
        assertCounts(0);
    }

    @Test
    void simultaneousDifferentInputsWithSameKeyProduceOneOrderAndOneConflict() throws Exception {
        UUID key = UUID.randomUUID();
        List<String> results = runTogether(List.of(() -> attempt(request(key, "first")),
                () -> attempt(request(key, "second"))));
        assertThat(results).containsExactlyInAnyOrder("CREATED", "IDEMPOTENCY_KEY_REUSED");
        assertCounts(1);
    }

    @Test
    void lockedProductChangeIsSeenBeforeCreatingOrder() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var started = new CountDownLatch(1);
        var request = request(UUID.randomUUID(), "text");
        var future = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<String>>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                source.lockProduct(productId).orElseThrow();
                future.set(executor.submit(() -> {
                    started.countDown();
                    try {
                        service.create(buyerId, request);
                        return "CREATED";
                    } catch (BusinessException e) {
                        return e.code();
                    }
                }));
                try {
                    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                jdbc.update("UPDATE products SET version = version + 1, price = 200000 WHERE id = ?", productId);
            });
            assertThat(future.get().get(20, TimeUnit.SECONDS)).isEqualTo("PRODUCT_VERSION_CONFLICT");
            assertCounts(0);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void newOrdersStayLegacyWithoutTradeTermsUntilContractCreationIsEnabled() {
        var result = service.create(buyerId, request(UUID.randomUUID(), "요구사항"));
        assertThat(policyOf(result.orderId())).isEqualTo("LEGACY_V1");
        assertThat(tradeTermsCount(result.orderId())).isZero();
    }

    @Test
    void contractOrderFixesPolicyAndStoresFixedTradeTermsInSameTransaction() {
        enableContractCreation();
        var result = service.create(buyerId, request(UUID.randomUUID(), "요구사항"));
        assertThat(result.created()).isTrue();
        assertCounts(1);
        assertThat(policyOf(result.orderId())).isEqualTo("CONTRACT_V2");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, result.orderId()))
                .isEqualTo("REQUESTED");
        var row = jdbc.queryForMap("SELECT * FROM order_trade_terms WHERE order_id = ?", result.orderId());
        assertThat(((Number) row.get("deposit_rate_bps")).intValue()).isEqualTo(5000);
        assertThat(((Number) row.get("compensation_rate_bps")).intValue()).isEqualTo(3000);
        assertThat(((Number) row.get("normal_fee_rate_bps")).intValue()).isEqualTo(1000);
        assertThat(((Number) row.get("compensation_fee_rate_bps")).intValue()).isEqualTo(100);
        assertThat(((Number) row.get("review_wait_hours")).intValue()).isEqualTo(48);
        assertThat(((Number) row.get("response_window_hours")).intValue()).isEqualTo(168);
    }

    @Test
    void fullFundingOrderFreezesNewTermsAndReplayDoesNotChangePolicy() {
        doReturn(TradePolicyVersion.CONTRACT_V3).when(newOrderPolicy).current();
        var key=UUID.randomUUID();
        var result=service.create(buyerId,request(key,"요구사항"));
        assertThat(policyOf(result.orderId())).isEqualTo("CONTRACT_V3");
        assertThat(jdbc.queryForObject("SELECT funding_mode FROM order_trade_terms WHERE order_id=?",String.class,result.orderId())).isEqualTo("FULL_HOLD");
        var frozen=snapshots.findById(result.orderId()).orElseThrow().getTermsSnapshot();
        assertThat(frozen).contains("자동 구매 확정","전액","구매에 사용할 수 있는 적립금").doesNotContain("판매 수익은 분리");
        doReturn(TradePolicyVersion.CONTRACT_V2).when(newOrderPolicy).current();
        assertThat(service.create(buyerId,request(key,"요구사항")).created()).isFalse();
        assertThat(policyOf(result.orderId())).isEqualTo("CONTRACT_V3");
        assertThat(snapshots.findById(result.orderId()).orElseThrow().getTermsSnapshot()).isEqualTo(frozen);
    }

    @Test
    void contractOrderRejectsTotalBelowMinimumWithoutWritingRows() {
        enableContractCreation();
        jdbc.update("UPDATE products SET price = 9999 WHERE id = ?", productId);
        assertBusiness(() -> service.create(buyerId, request(UUID.randomUUID(), "요구사항")),
                "CONTRACT_MINIMUM_AMOUNT_NOT_MET");
        assertCounts(0);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_trade_terms t JOIN orders o ON o.id = t.order_id WHERE o.product_id = ?",
                Long.class, productId)).isZero();
    }

    @Test
    void contractOrderAcceptsMinimumBoundary() {
        enableContractCreation();
        jdbc.update("UPDATE products SET price = 10000 WHERE id = ?", productId);
        var result = service.create(buyerId, request(UUID.randomUUID(), "요구사항"));
        assertThat(policyOf(result.orderId())).isEqualTo("CONTRACT_V2");
        assertThat(tradeTermsCount(result.orderId())).isOne();
    }

    @Test
    void replayKeepsPolicyFixedAtCreationEvenAfterSwitch() {
        UUID legacyKey = UUID.randomUUID();
        var legacy = service.create(buyerId, request(legacyKey, "요구사항"));
        enableContractCreation();
        var legacyReplay = service.create(buyerId, request(legacyKey, "요구사항"));
        assertThat(legacyReplay.created()).isFalse();
        assertThat(legacyReplay.orderId()).isEqualTo(legacy.orderId());
        assertThat(policyOf(legacy.orderId())).isEqualTo("LEGACY_V1");
        assertThat(tradeTermsCount(legacy.orderId())).isZero();

        UUID contractKey = UUID.randomUUID();
        var contract = service.create(buyerId, request(contractKey, "요구사항"));
        var contractReplay = service.create(buyerId, request(contractKey, "요구사항"));
        assertThat(contractReplay.created()).isFalse();
        assertThat(contractReplay.orderId()).isEqualTo(contract.orderId());
        assertThat(tradeTermsCount(contract.orderId())).isOne();
        assertCounts(2);
    }

    private void enableContractCreation() {
        doReturn(TradePolicyVersion.CONTRACT_V2).when(newOrderPolicy).current();
    }

    private String policyOf(long orderId) {
        return jdbc.queryForObject("SELECT policy_version FROM orders WHERE id = ?", String.class, orderId);
    }

    private long tradeTermsCount(long orderId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM order_trade_terms WHERE order_id = ?", Long.class, orderId);
    }

    private CreateOrderRequest request(UUID key, String requirements) {
        return new CreateOrderRequest(Long.toString(productId), 0L, requirements, key.toString());
    }

    private String attempt(CreateOrderRequest request) {
        try {
            return service.create(buyerId, request).created() ? "CREATED" : "REPLAY";
        } catch (BusinessException e) {
            return e.code();
        }
    }

    private <T> List<T> runTogether(List<java.util.concurrent.Callable<T>> tasks) throws Exception {
        var executor = Executors.newFixedThreadPool(tasks.size());
        var ready = new CountDownLatch(tasks.size());
        var start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<T>>();
            for (var task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results = new ArrayList<T>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private void assertCounts(long expected) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE product_id = ?", Long.class, productId)).isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_snapshots s JOIN orders o ON o.id = s.order_id WHERE o.product_id = ?", Long.class, productId)).isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_histories h JOIN orders o ON o.id = h.order_id WHERE o.product_id = ?", Long.class, productId)).isEqualTo(expected);
    }

    private void assertBusiness(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    private long member() {
        long id = insert("INSERT INTO members(email, password_hash, display_name, created_at, updated_at) VALUES (?, 'fixture-hash', 'creation fixture', ?, ?)",
                UUID.randomUUID() + "@creation.test", FIXTURE_TIME, FIXTURE_TIME);
        members.add(id);
        return id;
    }

    private long insert(String sql, Object... values) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock orderTestClock() {
            return Clock.fixed(Instant.parse("2026-11-02T06:00:00.123456789Z"), ZoneId.of("Asia/Seoul"));
        }
    }
}
