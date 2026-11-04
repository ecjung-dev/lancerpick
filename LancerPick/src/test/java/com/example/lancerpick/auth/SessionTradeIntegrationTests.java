package com.example.lancerpick.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.lancerpick.support.IntegrationTest;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회원가입·로그인·CSRF API가 발급한 실제 HttpSession으로 거래를 진행한다.
 * 테스트 principal·WithMockUser·csrf() 후처리기를 사용하지 않는다.
 * 상품 등록은 담당 범위 밖이므로 상품·카테고리·판매자 프로필만 SQL fixture로 준비한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class SessionTradeIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    private final List<Long> members = new ArrayList<>();
    private Account buyer, seller, outsider;
    private long category, product;

    record Account(long id, MockHttpSession session) {}
    record Token(String header, String value) {}

    @BeforeEach
    void prepareAccountsAndProduct() throws Exception {
        buyer = registerAndLogin();
        seller = registerAndLogin();
        outsider = registerAndLogin();
        var now = LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO seller_profiles(member_id,introduction,specialty,created_at,updated_at) VALUES (?,'profile','specialty',?,?)", seller.id(), now, now);
        category = insert("INSERT INTO categories(name,display_order) VALUES (?,0)", "session-" + UUID.randomUUID());
        product = insert("""
                INSERT INTO products(seller_id,category_id,title,description,scope_description,
                    preparation_description,price,duration_days,created_at,updated_at)
                VALUES (?,?,'session product','description','scope','prepare',999,1,?,?)
                """, seller.id(), category, now, now);
    }

    @AfterEach
    void removeOnlyOwnFixtures() {
        for (long member : members) jdbc.update("DELETE FROM wallet_entries WHERE member_id=?", member);
        var orders = jdbc.queryForList("SELECT id FROM orders WHERE product_id=?", Long.class, product);
        for (long order : orders) {
            for (String table : List.of("reviews", "settlements", "revision_requests", "deliveries", "cancellation_requests", "order_histories", "order_snapshots", "payments")) {
                jdbc.update("DELETE FROM " + table + " WHERE order_id=?", order);
            }
            jdbc.update("DELETE FROM orders WHERE id=?", order);
        }
        jdbc.update("DELETE FROM product_moderation_logs WHERE product_id=?", product);
        jdbc.update("DELETE FROM products WHERE id=?", product);
        jdbc.update("DELETE FROM categories WHERE id=?", category);
        for (long member : members) {
            jdbc.update("DELETE FROM seller_profiles WHERE member_id=?", member);
            jdbc.update("DELETE FROM wallets WHERE member_id=?", member);
            jdbc.update("DELETE FROM members WHERE id=?", member);
        }
    }

    @Test
    void completionUsesServerAmountAndSettlesOnceWithoutCreditingSellerWallet() throws Exception {
        String order = paidOrder();
        write(buyer, path(order, "payment"), Map.of("agreedToTerms", true)).andExpect(status().isOk());
        deliver(order, 1);
        write(buyer, path(order, "confirm"), null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        write(buyer, path(order, "confirm"), null).andExpect(status().isOk());
        read(buyer, "/api/me/wallet").andExpect(jsonPath("$.balance").value(999001));
        read(seller, "/api/me/wallet").andExpect(jsonPath("$.balance").value(1000000));
        read(seller, "/api/me/settlements/summary").andExpect(jsonPath("$.completedCount").value(1))
                .andExpect(jsonPath("$.grossAmount").value(999)).andExpect(jsonPath("$.feeAmount").value(99)).andExpect(jsonPath("$.sellerAmount").value(900));
        read(buyer, "/api/me/settlements/summary").andExpect(jsonPath("$.completedCount").value(0));
        read(outsider, "/api/me/settlements").andExpect(jsonPath("$.items.length()").value(0));
        assertThat(count("payments", order)).isEqualTo(1);
        assertThat(count("settlements", order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_histories WHERE order_id=? AND action='COMPLETED'", Long.class, order)).isEqualTo(1);
    }

    @Test
    void approvedCancellationRestoresFundsOnceAndBlocksConfirmationWhilePending() throws Exception {
        String order = paidOrder();
        var delivery = deliver(order, 1).get("deliveries").get(0).get("id").asText();
        var request = json(write(buyer, path(order, "cancellation-requests"), Map.of("reason", "cancel", "requestKey", UUID.randomUUID().toString()))
                .andExpect(status().isCreated()));
        String requestId = request.get("cancellationRequest").get("id").asText();
        write(buyer, path(order, "confirm"), null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANCELLATION_PENDING"));
        write(buyer, path(order, "revision-request"), Map.of("deliveryId", delivery, "reason", "revise"))
                .andExpect(status().isConflict());
        String approve = path(order, "cancellation-requests/" + requestId + "/approve");
        write(seller, approve, null).andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("CANCELLED"));
        write(seller, approve, null).andExpect(status().isOk());
        write(buyer, path(order, "payment"), Map.of("agreedToTerms", true)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_REFUNDED"));
        read(buyer, "/api/me/wallet").andExpect(jsonPath("$.balance").value(1000000));
        assertThat(refunds(order)).isEqualTo(1);
        assertThat(count("settlements", order)).isZero();
    }

    @Test
    void rejectionWithdrawalAndOneRevisionCanContinueToCompletion() throws Exception {
        String order = paidOrder();
        var first = json(write(buyer, path(order, "cancellation-requests"), Map.of("reason", "first", "requestKey", UUID.randomUUID().toString()))
                .andExpect(status().isCreated()));
        write(seller, path(order, "cancellation-requests/" + first.get("cancellationRequest").get("id").asText() + "/reject"), Map.of("responseReason", "continue"))
                .andExpect(status().isOk());
        var body = Map.of("reason", "second", "requestKey", UUID.randomUUID().toString());
        var second = json(write(buyer, path(order, "cancellation-requests"), body).andExpect(status().isCreated()));
        write(buyer, path(order, "cancellation-requests/" + second.get("cancellationRequest").get("id").asText() + "/withdraw"), null)
                .andExpect(status().isOk());
        write(buyer, path(order, "cancellation-requests"), body).andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationRequest.status").value("WITHDRAWN"));
        var delivery = deliver(order, 1).get("deliveries").get(0).get("id").asText();
        write(buyer, path(order, "revision-request"), Map.of("deliveryId", delivery, "reason", "revise"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVISION_IN_PROGRESS"));
        deliver(order, 2);
        write(buyer, path(order, "revision-request"), Map.of("deliveryId", delivery, "reason", "again")).andExpect(status().isConflict());
        write(buyer, path(order, "confirm"), null).andExpect(status().isOk());
        assertThat(count("cancellation_requests", order)).isEqualTo(2);
        assertThat(count("settlements", order)).isEqualTo(1);
        assertThat(refunds(order)).isZero();
    }

    @Test
    void realSessionOwnershipCsrfAndLogoutAreEnforced() throws Exception {
        String order = paidOrder();
        read(outsider, "/api/orders/" + order).andExpect(status().isNotFound());
        write(outsider, path(order, "confirm"), null).andExpect(status().isNotFound());
        write(seller, path(order, "payment"), Map.of("agreedToTerms", true)).andExpect(status().isForbidden());
        mvc.perform(post(path(order, "cancel")).session(seller.session()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"cancel\"}")).andExpect(status().isForbidden());
        var wrong = token(outsider.session());
        mvc.perform(post(path(order, "cancel")).session(seller.session()).header(wrong.header(), wrong.value())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"cancel\"}")).andExpect(status().isForbidden());
        write(buyer, "/api/auth/logout", null).andExpect(status().isNoContent());
        read(buyer, "/api/orders/" + order).andExpect(status().isUnauthorized());
        read(buyer, "/api/me/wallet").andExpect(status().isUnauthorized());
        assertThat(refunds(order)).isZero();
    }

    @RepeatedTest(3)
    void cancellationAndConfirmationCannotBothCommitWithRealSessions() throws Exception {
        String order = paidOrder();
        deliver(order, 1);
        var buyerToken = token(buyer.session());
        var sellerToken = token(seller.session());
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var confirm = pool.submit(() -> { start.await(); return postWithToken(buyer, buyerToken, path(order, "confirm"), null).andReturn().getResponse().getStatus(); });
            var cancel = pool.submit(() -> { start.await(); return postWithToken(seller, sellerToken, path(order, "cancel"), Map.of("reason", "cancel")).andReturn().getResponse().getStatus(); });
            start.countDown();
            assertThat(List.of(confirm.get(30, TimeUnit.SECONDS), cancel.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        String state = json(read(buyer, "/api/orders/" + order).andExpect(status().isOk())).get("status").asText();
        boolean complete = state.equals("COMPLETED");
        assertThat(state).isIn("COMPLETED", "CANCELLED");
        assertThat(count("settlements", order)).isEqualTo(complete ? 1 : 0);
        assertThat(refunds(order)).isEqualTo(complete ? 0 : 1);
        read(buyer, "/api/me/wallet").andExpect(jsonPath("$.balance").value(complete ? 999001 : 1000000));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDIT", "STOP", "HIDE"})
    void productChangePreservesReplayAndExistingOrderThroughCompletion(String change) throws Exception {
        var input = orderInput();
        var created = json(write(buyer, "/api/orders", input).andExpect(status().isCreated()));
        String order = created.get("id").asText();
        changeProduct(change);

        read(buyer, "/api/orders/" + order).andExpect(status().isOk());
        var replay = json(write(buyer, "/api/orders", input).andExpect(status().isOk()));
        assertSavedConditions(created, replay);
        assertThat(replay.get("id").asText()).isEqualTo(order);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE product_id=?", Long.class, product)).isEqualTo(1);
        var fresh = Map.of("productId", Long.toString(product), "expectedProductVersion", change.equals("EDIT") ? 0 : 1,
                "requirements", "new request", "requestKey", UUID.randomUUID().toString());
        var attempt = write(buyer, "/api/orders", fresh);
        if (change.equals("HIDE")) attempt.andExpect(status().isNotFound());
        else attempt.andExpect(status().isConflict()).andExpect(jsonPath("$.code")
                .value(change.equals("EDIT") ? "PRODUCT_VERSION_CONFLICT" : "PRODUCT_NOT_ORDERABLE"));

        write(seller, path(order, "accept"), null).andExpect(status().isOk());
        var paid = json(write(buyer, path(order, "payment"), Map.of("agreedToTerms", true)).andExpect(status().isOk()));
        assertSavedConditions(created, paid);
        assertThat(paid.get("payment").get("amount").asLong()).isEqualTo(999);
        assertThat(java.time.Duration.between(java.time.OffsetDateTime.parse(paid.get("paidAt").asText()),
                java.time.OffsetDateTime.parse(paid.get("originalDueAt").asText()))).isEqualTo(java.time.Duration.ofDays(1));
        String delivery = deliver(order, 1).get("deliveries").get(0).get("id").asText();
        write(buyer, path(order, "revision-request"), Map.of("deliveryId", delivery, "reason", "revise"))
                .andExpect(status().isOk());
        deliver(order, 2);
        var completed = json(write(buyer, path(order, "confirm"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED")));
        assertSavedConditions(created, completed);
        read(buyer, "/api/me/wallet").andExpect(jsonPath("$.balance").value(999001));
        read(seller, "/api/me/settlements/summary").andExpect(jsonPath("$.completedCount").value(1))
                .andExpect(jsonPath("$.grossAmount").value(999)).andExpect(jsonPath("$.feeAmount").value(99))
                .andExpect(jsonPath("$.sellerAmount").value(900));
        assertThat(count("settlements", order)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDIT", "STOP", "HIDE"})
    void productChangeKeepsDirectAndApprovedRefundsOnOriginalPrice(String change) throws Exception {
        String direct = paidOrder();
        String approved = paidOrder();
        changeProduct(change);
        deliver(direct, 1);
        deliver(approved, 1);
        String cancel = path(direct, "cancel");
        write(seller, cancel, Map.of("reason", "cancel")).andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.amount").value(999)).andExpect(jsonPath("$.status").value("CANCELLED"));
        write(seller, cancel, Map.of("reason", "cancel")).andExpect(status().isOk());
        var request = json(write(buyer, path(approved, "cancellation-requests"),
                Map.of("reason", "cancel", "requestKey", UUID.randomUUID().toString())).andExpect(status().isCreated()));
        String approve = path(approved, "cancellation-requests/" + request.get("cancellationRequest").get("id").asText() + "/approve");
        write(seller, approve, null).andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("CANCELLED"))
                .andExpect(jsonPath("$.order.payment.amount").value(999));
        write(seller, approve, null).andExpect(status().isOk());
        read(buyer, "/api/me/wallet").andExpect(jsonPath("$.balance").value(1000000));
        for (String order : List.of(direct, approved)) {
            assertThat(refunds(order)).isEqualTo(1);
            assertThat(count("settlements", order)).isZero();
            assertThat(jdbc.queryForObject("SELECT amount_delta FROM wallet_entries e JOIN payments p ON p.id=e.payment_id WHERE p.order_id=? AND e.type='REFUND'", Long.class, order)).isEqualTo(999);
        }
    }

    private Map<String, Object> orderInput() {
        return Map.of("productId", Long.toString(product), "expectedProductVersion", 0,
                "requirements", "requirements", "requestKey", UUID.randomUUID().toString());
    }

    private void assertSavedConditions(JsonNode before, JsonNode after) {
        for (String field : List.of("productSnapshot", "termsSnapshot", "priceAmount", "durationDays", "requirements")) {
            assertThat(after.get(field)).as("saved %s", field).isEqualTo(before.get(field));
        }
    }

    private void changeProduct(String change) throws Exception {
        String endpoint = "/api/products/" + product;
        if (change.equals("EDIT")) {
            patchWithToken(seller, endpoint, Map.of("title", "changed", "description", "changed description",
                    "scopeDescription", "changed scope", "preparationDescription", "changed prep",
                    "priceAmount", 50000, "durationDays", 3, "expectedVersion", 0))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
            read(buyer, endpoint).andExpect(status().isOk()).andExpect(jsonPath("$.priceAmount").value(50000));
        } else if (change.equals("STOP")) {
            patchWithToken(seller, endpoint + "/sale-status", Map.of("saleStatus", "STOPPED", "expectedVersion", 0))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.saleStatus").value("STOPPED"));
            read(buyer, endpoint).andExpect(status().isNotFound());
        } else {
            Account admin = registerAndLogin(true);
            patchWithToken(admin, "/api/admin/products/" + product + "/visibility",
                    Map.of("hidden", true, "reason", "moderation", "expectedVersion", 0))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.isHidden").value(true));
            read(buyer, endpoint).andExpect(status().isNotFound());
        }
    }

    private ResultActions patchWithToken(Account account, String path, Object body) throws Exception {
        var token = token(account.session());
        return mvc.perform(patch(path).session(account.session()).header(token.header(), token.value())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }

    private String paidOrder() throws Exception {
        var created = json(write(buyer, "/api/orders", Map.of("productId", Long.toString(product), "expectedProductVersion", 0,
                "requirements", "requirements", "requestKey", UUID.randomUUID().toString())).andExpect(status().isCreated()));
        String id = created.get("id").asText();
        write(seller, path(id, "accept"), null).andExpect(status().isOk());
        write(buyer, path(id, "payment"), Map.of("agreedToTerms", true, "amount", 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.amount").value(999));
        return id;
    }

    private JsonNode deliver(String order, int sequence) throws Exception {
        return json(write(seller, path(order, "deliveries"), Map.of("description", "result", "resultUrl", "https://example.com/result", "expectedSequenceNo", sequence))
                .andExpect(status().isOk()));
    }

    private Account registerAndLogin() throws Exception {
        return registerAndLogin(false);
    }

    private Account registerAndLogin(boolean admin) throws Exception {
        var session = new MockHttpSession();
        var anonymous = new Account(0, session);
        String email = "trade-" + UUID.randomUUID() + "@example.com";
        var created = json(write(anonymous, "/api/members", Map.of("email", email, "password", "abcd1234", "displayName", "session-member"))
                .andExpect(status().isCreated()));
        long id = Long.parseLong(created.get("id").asText());
        members.add(id);
        // 테스트 회원에만 관리자 권한을 준비하고 실제 로그인 API로 세션을 발급한다.
        if (admin) jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", id);
        write(anonymous, "/api/auth/login", Map.of("email", email, "password", "abcd1234")).andExpect(status().isOk());
        return new Account(id, session);
    }

    private Token token(MockHttpSession session) throws Exception {
        var csrf = json(mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()));
        return new Token(csrf.get("headerName").asText(), csrf.get("token").asText());
    }

    private ResultActions write(Account account, String path, Object body) throws Exception { return postWithToken(account, token(account.session()), path, body); }
    private ResultActions postWithToken(Account account, Token token, String path, Object body) throws Exception {
        MockHttpServletRequestBuilder request = post(path).session(account.session()).header(token.header(), token.value());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        return mvc.perform(request);
    }
    private ResultActions read(Account account, String path) throws Exception { return mvc.perform(get(path).session(account.session())); }
    private JsonNode json(ResultActions result) throws Exception { return mapper.readTree(result.andReturn().getResponse().getContentAsString()); }
    private static String path(String order, String action) { return "/api/orders/" + order + "/" + action; }
    private long count(String table, String order) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE order_id=?", Long.class, order); }
    private long refunds(String order) { return jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entries e JOIN payments p ON p.id=e.payment_id WHERE p.order_id=? AND e.type='REFUND'", Long.class, order); }
    private long insert(String sql, Object... args) {
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> { var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS); for (int i=0; i<args.length; i++) statement.setObject(i+1,args[i]); return statement; }, key);
        return key.getKey().longValue();
    }
}
