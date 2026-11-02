package com.example.lancerpick.order;

import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderHistory;
import com.example.lancerpick.order.entity.OrderHistoryAction;
import com.example.lancerpick.order.entity.OrderSnapshot;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.repository.OrderHistoryRepository;
import com.example.lancerpick.order.repository.OrderRepository;
import com.example.lancerpick.order.repository.OrderSnapshotRepository;
import com.example.lancerpick.support.IntegrationTest;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 저장 Repository의 통합 검사. 주문 생성 서비스나 인증·상품 검사 완료를 의미하지 않는다. */
@IntegrationTest
class OrderPersistenceIntegrationTests {
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-11-02T06:00:00.123456");

    @Autowired OrderRepository orders;
    @Autowired OrderSnapshotRepository snapshots;
    @Autowired OrderHistoryRepository histories;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private final List<Long> members = new ArrayList<>();
    private long buyerId;
    private long sellerId;
    private long categoryId;
    private long productId;

    @BeforeEach
    void createIsolatedFixtures() {
        buyerId = insertMember();
        sellerId = insertMember();
        categoryId = insert("INSERT INTO categories(name, display_order) VALUES (?, 0)",
                "order-test-" + UUID.randomUUID());
        productId = insert("""
                INSERT INTO products(seller_id, category_id, title, description, scope_description,
                    preparation_description, price, duration_days, created_at, updated_at)
                VALUES (?, ?, 'original title', 'description', 'scope', 'preparation', 100000, 3, ?, ?)
                """, sellerId, categoryId, NOW, NOW);
    }

    @AfterEach
    void deleteOnlyOwnedFixtures() {
        if (productId != 0) {
            jdbc.update("DELETE FROM order_histories WHERE order_id IN (SELECT id FROM orders WHERE product_id = ?)", productId);
            jdbc.update("DELETE FROM order_snapshots WHERE order_id IN (SELECT id FROM orders WHERE product_id = ?)", productId);
            jdbc.update("DELETE FROM orders WHERE product_id = ?", productId);
            jdbc.update("DELETE FROM products WHERE id = ?", productId);
        }
        if (categoryId != 0) jdbc.update("DELETE FROM categories WHERE id = ?", categoryId);
        for (long id : members) jdbc.update("DELETE FROM members WHERE id = ?", id);
    }

    @Test
    void savesAndReloadsOrderSnapshotAndInitialHistoryAcrossTransactions() {
        UUID key = UUID.randomUUID();
        long id = new TransactionTemplate(transactionManager).execute(status -> {
            Order order = saveOrder(key, buyerId);
            OrderSnapshot snapshot = snapshot(order.getId(), " original title ");
            assertThat(snapshot.isNew()).isTrue();
            snapshots.saveAndFlush(snapshot);
            assertThat(snapshot.isNew()).isFalse();
            histories.saveAndFlush(OrderHistory.created(order.getId(), buyerId, NOW));
            return order.getId();
        });

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Order order = orders.findByBuyerIdAndRequestKey(buyerId, key.toString()).orElseThrow();
            assertThat(order.getId()).isEqualTo(id);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.REQUESTED);
            assertThat(order.getCreatedAt()).isEqualTo(NOW);
            OrderSnapshot snapshot = snapshots.findById(id).orElseThrow();
            assertThat(snapshot.isNew()).isFalse();
            assertThat(snapshot.getProductTitleSnapshot()).isEqualTo("original title");
            assertThat(snapshot.getRequirements()).isEqualTo("첫 줄\n다음 줄 😀");
            assertThat(snapshot.getProductVersionSnapshot()).isZero();
            var history = histories.findByOrderIdOrderByCreatedAtAscIdAsc(id);
            assertThat(history).hasSize(1);
            assertThat(history.getFirst().getActorId()).isEqualTo(buyerId);
            assertThat(history.getFirst().getAction()).isEqualTo(OrderHistoryAction.CREATED);
            assertThat(history.getFirst().getFromStatus()).isNull();
            assertThat(history.getFirst().getToStatus()).isEqualTo(OrderStatus.REQUESTED);
            assertThat(history.getFirst().getReason()).isNull();
            assertThat(history.getFirst().getCreatedAt()).isEqualTo(NOW);
        });

        jdbc.update("UPDATE products SET title = 'changed', price = 200000, version = version + 1 WHERE id = ?", productId);
        assertThat(snapshots.findById(id).orElseThrow().getProductTitleSnapshot()).isEqualTo("original title");
        assertThat(jdbc.queryForObject("SELECT price_amount FROM orders WHERE id = ?", Long.class, id))
                .isEqualTo(100000L);
        assertThat(jdbc.queryForObject("SELECT duration_days_snapshot FROM orders WHERE id = ?", Integer.class, id))
                .isEqualTo(3);
    }

    @Test
    void snapshotForeignKeyFailureRollsBackTheInsertedOrder() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            saveOrder(UUID.randomUUID(), buyerId);
            snapshots.saveAndFlush(snapshot(Long.MAX_VALUE, "title"));
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertNoOrderRows();
    }

    @Test
    void historyForeignKeyFailureRollsBackOrderAndSnapshot() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Order order = saveOrder(UUID.randomUUID(), buyerId);
            snapshots.saveAndFlush(snapshot(order.getId(), "title"));
            histories.saveAndFlush(OrderHistory.created(order.getId(), Long.MAX_VALUE, NOW));
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertNoOrderRows();
    }

    @Test
    void sameBuyerAndRequestKeyCannotCreateTwoOrders() {
        UUID key = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> saveOrder(key, buyerId));

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> saveOrder(key, buyerId)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE product_id = ?", Long.class, productId))
                .isEqualTo(1L);
    }

    @Test
    void sameRequestKeyIsIndependentForDifferentBuyers() {
        long anotherBuyer = insertMember();
        UUID key = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            saveOrder(key, buyerId);
            saveOrder(key, anotherBuyer);
        });

        assertThat(orders.findByBuyerIdAndRequestKey(buyerId, key.toString())).isPresent();
        assertThat(orders.findByBuyerIdAndRequestKey(anotherBuyer, key.toString())).isPresent();
    }

    @Test
    void secondSnapshotFailsInsteadOfSilentlyMergingOverTheOriginal() {
        long id = new TransactionTemplate(transactionManager).execute(status -> {
            Order order = saveOrder(UUID.randomUUID(), buyerId);
            snapshots.saveAndFlush(snapshot(order.getId(), "original"));
            return order.getId();
        });

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                snapshots.saveAndFlush(snapshot(id, "replacement"))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(snapshots.findById(id).orElseThrow().getProductTitleSnapshot()).isEqualTo("original");
    }

    private Order saveOrder(UUID key, long buyer) {
        return orders.saveAndFlush(Order.create(buyer, sellerId, productId, key, 100000L, 3, NOW));
    }

    private OrderSnapshot snapshot(long id, String title) {
        return OrderSnapshot.create(id, 0L, title, "description", "scope", "preparation", "terms",
                "\u3000첫 줄\r\n다음 줄 😀\u00a0");
    }

    private void assertNoOrderRows() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE product_id = ?", Long.class, productId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_snapshots s JOIN orders o ON o.id = s.order_id WHERE o.product_id = ?", Long.class, productId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_histories h JOIN orders o ON o.id = h.order_id WHERE o.product_id = ?", Long.class, productId)).isZero();
    }

    private long insertMember() {
        long id = insert("""
                INSERT INTO members(email, password_hash, display_name, created_at, updated_at)
                VALUES (?, 'test-only-hash', 'order fixture', ?, ?)
                """, UUID.randomUUID() + "@order.test", NOW, NOW);
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
}
