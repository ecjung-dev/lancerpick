package com.example.lancerpick.order;

import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.order.dto.CreateOrderRequest;
import com.example.lancerpick.order.service.OrderCreationService;
import com.example.lancerpick.support.IntegrationTest;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 로그인 자체는 테스트 principal로 대체하고 실제 GET 보안·조회·MySQL을 확인한다. */
@IntegrationTest
@AutoConfigureMockMvc
@Import(OrderCreationApiIntegrationTests.FixedClock.class)
class OrderQueryApiIntegrationTests {
    private static final LocalDateTime AT = LocalDateTime.parse("2026-11-02T06:00:00.123456");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderCreationService creation;
    final List<Long> members = new ArrayList<>();
    final List<Long> products = new ArrayList<>();
    long buyer, seller, third, category, first, second, sold;

    record Principal(Long memberId) implements AuthenticatedMember {}

    @BeforeEach
    void fixtures() {
        buyer = member("buyer"); seller = member("seller"); third = member("third");
        category = insert("INSERT INTO categories(name,display_order) VALUES (?,0)", "query-" + UUID.randomUUID());
        long sellProduct = product(seller);
        long ownProduct = product(buyer);
        long thirdProduct = product(third);
        first = order(buyer, sellProduct);
        second = order(buyer, sellProduct);
        sold = order(third, ownProduct);
        order(seller, thirdProduct);
        jdbc.update("UPDATE orders SET status='CANCELLED',cancelled_at=created_at WHERE id=?", first);
    }

    @AfterEach
    void cleanup() {
        for (long product : products) {
            for (String table : List.of("revision_requests", "deliveries", "order_histories", "order_snapshots")) {
                jdbc.update("DELETE FROM " + table + " WHERE order_id IN (SELECT id FROM orders WHERE product_id=?)", product);
            }
            jdbc.update("DELETE FROM orders WHERE product_id=?", product);
            jdbc.update("DELETE FROM products WHERE id=?", product);
        }
        jdbc.update("DELETE FROM categories WHERE id=?", category);
        for (long id : members) jdbc.update("DELETE FROM members WHERE id=?", id);
    }

    @Test
    void buyerListUsesStableSortSavedTitleCounterpartyAndPagination() throws Exception {
        jdbc.update("UPDATE products SET title='changed',is_hidden=true WHERE id=?", products.getFirst());
        read("?side=BUYER&size=1", buyer).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(Long.toString(second)))
                .andExpect(jsonPath("$.items[0].productTitle").value("saved title"))
                .andExpect(jsonPath("$.items[0].priceAmount").value(100000))
                .andExpect(jsonPath("$.items[0].counterparty.id").value(Long.toString(seller)))
                .andExpect(jsonPath("$.items[0].counterparty.displayName").value("seller"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-11-02T06:00:00.123456Z"))
                .andExpect(jsonPath("$.items[0].currentDueAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].requestKey").doesNotExist())
                .andExpect(jsonPath("$.items[0].counterparty.email").doesNotExist());
        read("?side=BUYER&size=1&page=1", buyer).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(Long.toString(first)));
        read("?side=BUYER&status=CANCELLED", buyer).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(Long.toString(first)));
    }

    @Test
    void sellerSideUsesOppositeParticipantAndCannotBeOverriddenByQueryMemberId() throws Exception {
        read("?side=SELLER&memberId=" + seller, buyer).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(Long.toString(sold)))
                .andExpect(jsonPath("$.items[0].counterparty.id").value(Long.toString(third)));
    }

    @Test
    void emptyFilterAndHugePageKeepCorrectMetadata() throws Exception {
        read("?side=BUYER&status=COMPLETED", buyer).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0)).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
        read("?side=BUYER&page=2147483647&size=100", buyer).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void participantsGetRoleSpecificActionsAndOutsiderGetsSame404AsMissingOrder() throws Exception {
        read("/" + second, buyer).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.contains("CANCEL")));
        read("/" + second, seller).andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.containsInAnyOrder("ACCEPT", "REJECT")));
        read("/" + second, third).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        read("/9223372036854775807", third).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void listsAndDetailPreferRevisionDeadlineWithoutWritingRecords() throws Exception {
        jdbc.update("""
                UPDATE orders SET status='REVISION_IN_PROGRESS',accepted_at=created_at,buyer_confirmed_terms_at=created_at,
                    paid_at=created_at,due_at=DATE_ADD(created_at,INTERVAL 3 DAY) WHERE id=?
                """, second);
        long delivery = insert("INSERT INTO deliveries(order_id,sequence_no,description,result_url,submitted_at) VALUES (?,1,'first','https://example.com/result',?)", second, AT);
        insert("INSERT INTO revision_requests(order_id,delivery_id,reason,requested_at,due_at) VALUES (?,?,'revision',?,?)", second, delivery, AT, AT.plusDays(4));
        read("?side=BUYER&status=REVISION_IN_PROGRESS", buyer).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].currentDueAt").value("2026-11-06T06:00:00.123456Z"));
        read("/" + second, buyer).andExpect(status().isOk())
                .andExpect(jsonPath("$.originalDueAt").value("2026-11-05T06:00:00.123456Z"))
                .andExpect(jsonPath("$.currentDueAt").value("2026-11-06T06:00:00.123456Z"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_histories WHERE order_id=?", Long.class, second)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE order_id=?", Long.class, second)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?side=", "?side=buyer", "?side=OTHER", "?side=BUYER&status=", "?side=BUYER&status=other",
            "?side=BUYER&page=-1", "?side=BUYER&size=0", "?side=BUYER&size=101", "?side=BUYER&page=1.5", "?side=BUYER&page=2147483648",
            "/0", "/-1", "/abc", "/9223372036854775808"})
    void invalidQueriesAndIdsAre400(String suffix) throws Exception {
        read(suffix, buyer).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray()).andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void anonymousAndUnrecognizedPrincipalCannotReadEitherEndpoint() throws Exception {
        for (String path : List.of("/api/orders?side=BUYER", "/api/orders/" + second)) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(authentication(UsernamePasswordAuthenticationToken.authenticated("name", null,
                    AuthorityUtils.createAuthorityList("ROLE_USER"))))).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void missingSnapshotIsReportedInsteadOfSilentlyOmittingTheOrder() throws Exception {
        jdbc.update("DELETE FROM order_snapshots WHERE order_id=?", second);
        read("?side=BUYER", buyer).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_ERROR"));
        read("/" + second, buyer).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_ERROR"));
    }

    private ResultActions read(String suffix, long id) throws Exception {
        return mvc.perform(get("/api/orders" + suffix).with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                new Principal(id), null, AuthorityUtils.createAuthorityList("ROLE_USER")))));
    }
    private long order(long buyer, long product) {
        return creation.create(buyer, new CreateOrderRequest(Long.toString(product), 0L, "requirements", UUID.randomUUID().toString())).orderId();
    }
    private long member(String name) {
        long id = insert("INSERT INTO members(email,password_hash,display_name,created_at,updated_at) VALUES (?,'fixture',?,?,?)",
                UUID.randomUUID() + "@order-query.test", name, AT, AT);
        members.add(id); return id;
    }
    private long product(long seller) {
        long id = insert("""
                INSERT INTO products(seller_id,category_id,title,description,scope_description,preparation_description,
                    price,duration_days,created_at,updated_at) VALUES (?,?,'saved title','description','scope','prepare',100000,3,?,?)
                """, seller, category, AT, AT);
        products.add(id); return id;
    }
    private long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return key.getKey().longValue();
    }
}
