package com.example.lancerpick.order;

import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.order.repository.OrderDetailQueries;
import com.example.lancerpick.support.IntegrationTest;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 HTTP 필터→인증 계약→주문 트랜잭션→MySQL을 검사한다. 로그인 자체는 테스트 principal로 대체한다. */
@IntegrationTest
@AutoConfigureMockMvc
@Import(OrderCreationApiIntegrationTests.FixedClock.class)
class OrderCreationApiIntegrationTests {
    private static final LocalDateTime AT = LocalDateTime.parse("2026-11-02T06:00:00.123456");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @MockitoSpyBean OrderDetailQueries details;
    long buyer, seller, category, product;
    String key;

    record Principal(Long memberId) implements AuthenticatedMember {}

    @BeforeEach
    void fixtures() {
        buyer = member("buyer");
        seller = member("seller");
        category = insert("INSERT INTO categories(name,display_order) VALUES (?,0)", "api-" + UUID.randomUUID());
        product = insert("""
                INSERT INTO products(seller_id,category_id,title,description,scope_description,preparation_description,
                    price,duration_days,created_at,updated_at) VALUES (?,?,'original','description','scope','prepare',100000,3,?,?)
                """, seller, category, AT, AT);
        key = UUID.randomUUID().toString();
    }

    @AfterEach
    void cleanup() {
        reset(details);
        for (String table : new String[]{"reviews", "payments", "cancellation_requests", "revision_requests",
                "deliveries", "order_histories", "order_snapshots"}) {
            jdbc.update("DELETE FROM " + table + " WHERE order_id IN (SELECT id FROM orders WHERE product_id=?)", product);
        }
        jdbc.update("DELETE FROM orders WHERE product_id=?", product);
        jdbc.update("DELETE FROM products WHERE id=?", product);
        jdbc.update("DELETE FROM categories WHERE id=?", category);
        jdbc.update("DELETE FROM members WHERE id IN (?,?)", buyer, seller);
    }

    @Test
    void createsFullDetailWithServerIdentityConditionsAndUtc() throws Exception {
        var request = mapper.readTree(body()).deepCopy();
        ((tools.jackson.databind.node.ObjectNode) request).put("buyerId", Long.toString(seller));
        ((tools.jackson.databind.node.ObjectNode) request).put("priceAmount", 1);
        var response = send(mapper.writeValueAsString(request), buyer)
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").isString()).andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.buyer.id").value(Long.toString(buyer)))
                .andExpect(jsonPath("$.seller.id").value(Long.toString(seller)))
                .andExpect(jsonPath("$.productId").value(Long.toString(product)))
                .andExpect(jsonPath("$.productSnapshot.title").value("original"))
                .andExpect(jsonPath("$.productSnapshot.productVersion").value(0))
                .andExpect(jsonPath("$.priceAmount").value(100000)).andExpect(jsonPath("$.durationDays").value(3))
                .andExpect(jsonPath("$.requirements").value("요구사항\n다음 줄"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-02T06:00:00.123456Z"))
                .andExpect(jsonPath("$.histories[0].actorId").value(Long.toString(buyer)))
                .andExpect(jsonPath("$.histories[0].action").value("CREATED"))
                .andExpect(jsonPath("$.histories[0].fromStatus").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.allowedActions[0]").value("CANCEL"))
                .andExpect(jsonPath("$.deliveries").isEmpty()).andExpect(jsonPath("$.cancellationRequests").isEmpty())
                .andExpect(jsonPath("$.payment").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.review").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.revisionRequest").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.buyer.email").doesNotExist()).andExpect(jsonPath("$.requestKey").doesNotExist())
                .andReturn();
        var json = mapper.readTree(response.getResponse().getContentAsString());
        for (String field : new String[]{"acceptedAt", "buyerConfirmedTermsAt", "paidAt", "originalDueAt",
                "revisionDueAt", "currentDueAt", "completedAt", "cancelledAt"}) {
            assertThat(json.has(field)).as(field).isTrue();
            assertThat(json.get(field).isNull()).as(field).isTrue();
        }
        assertCounts(1);
    }

    @Test
    void replayReturnsCurrentOrderAndSavedSnapshotDespiteProductChanges() throws Exception {
        String id = mapper.readTree(send(body(), buyer).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString()).get("id").asString();
        jdbc.update("UPDATE products SET title='changed',price=200000,is_hidden=true,version=1 WHERE id=?", product);
        jdbc.update("UPDATE orders SET status='AWAITING_PAYMENT',accepted_at=created_at WHERE id=?", id);
        send(body(), buyer).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.productSnapshot.title").value("original"))
                .andExpect(jsonPath("$.priceAmount").value(100000))
                .andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.containsInAnyOrder("PAY", "CANCEL")));
        assertCounts(1);
    }

    @Test
    void keyReuseWithDifferentInputIs409() throws Exception {
        send(body(), buyer).andExpect(status().isCreated());
        send(body().replace("요구사항", "다른 요구"), buyer).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertCounts(1);
    }

    @Test
    void replayIncludesLaterDeliveriesRevisionPaymentReviewAndCancellationHistory() throws Exception {
        String id = mapper.readTree(send(body(), buyer).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString()).get("id").asString();
        jdbc.update("""
                UPDATE orders SET status='COMPLETED',accepted_at=created_at,buyer_confirmed_terms_at=created_at,
                    paid_at=created_at,due_at=DATE_ADD(created_at,INTERVAL 3 DAY),completed_at=created_at WHERE id=?
                """, id);
        long delivery = insert("INSERT INTO deliveries(order_id,sequence_no,description,result_url,submitted_at) VALUES (?,1,'first','https://example.com/result',?)", id, AT);
        insert("INSERT INTO revision_requests(order_id,delivery_id,reason,requested_at,due_at) VALUES (?,?,'revision',?,?)", id, delivery, AT, AT.plusDays(4));
        insert("INSERT INTO deliveries(order_id,sequence_no,description,result_url,submitted_at) VALUES (?,2,'second','https://example.com/revised',?)", id, AT);
        insert("INSERT INTO payments(order_id,amount,fee_rate_bps,status,paid_at) VALUES (?,100000,1000,'PAID',?)", id, AT);
        insert("INSERT INTO reviews(order_id,rating,content,created_at) VALUES (?,5,NULL,?)", id, AT);
        insert("""
                INSERT INTO cancellation_requests(order_id,requested_by,request_key,reason,status,requested_at,resolved_at,resolved_by)
                VALUES (?,?,?,'cancel','REJECTED',?,?,?)
                """, id, buyer, UUID.randomUUID().toString(), AT, AT, seller);
        send(body(), buyer).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.deliveries[0].sequenceNo").value(1))
                .andExpect(jsonPath("$.deliveries[1].sequenceNo").value(2))
                .andExpect(jsonPath("$.revisionRequest.deliveryId").value(Long.toString(delivery)))
                .andExpect(jsonPath("$.revisionDueAt").value("2026-11-06T06:00:00.123456Z"))
                .andExpect(jsonPath("$.currentDueAt").value("2026-11-06T06:00:00.123456Z"))
                .andExpect(jsonPath("$.payment.amount").value(100000))
                .andExpect(jsonPath("$.payment.refundedAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.review.orderId").value(id))
                .andExpect(jsonPath("$.review.authorDisplayName").value("buyer"))
                .andExpect(jsonPath("$.cancellationRequests[0].resolvedBy").value(Long.toString(seller)))
                .andExpect(jsonPath("$.allowedActions").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0", "0.5", "\"0\"", "true", "9223372036854775808"})
    void rejectsNonIntegerOrOverflowVersionInOtherwiseValidRequest(String version) throws Exception {
        send(body().replace("\"expectedProductVersion\":0", "\"expectedProductVersion\":" + version), buyer)
                .andExpect(status().isBadRequest());
        assertCounts(0);
    }

    @Test
    void rejectsNumericProductIdInOtherwiseValidRequest() throws Exception {
        send(body().replace("\"productId\":\"" + product + "\"", "\"productId\":" + product), buyer)
                .andExpect(status().isBadRequest());
        assertCounts(0);
    }

    @Test
    void anonymousWithCsrfIs401() throws Exception {
        mvc.perform(post("/api/orders").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        assertCounts(0);
    }

    @Test
    void missingCsrfIs403() throws Exception {
        mvc.perform(post("/api/orders").with(authentication(auth(buyer))).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertCounts(0);
    }

    @Test
    void principalWithoutMemberContractIs401() throws Exception {
        mvc.perform(post("/api/orders").with(csrf()).with(authentication(
                        UsernamePasswordAuthenticationToken.authenticated("username", null, AuthorityUtils.createAuthorityList("ROLE_USER"))))
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isUnauthorized());
        assertCounts(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"expectedProductVersion\":0,\"requirements\":\"x\",\"requestKey\":\"bad\"}",
            "{\"productId\":1,\"expectedProductVersion\":0}", "{\"productId\":\"1\",\"expectedProductVersion\":0.5}",
            "{\"productId\":\"1\",\"expectedProductVersion\":\"0\"}", "{\"productId\":\"1\",\"expectedProductVersion\":true}", "[]"})
    void invalidJsonTypesAndMissingFieldsAre400(String json) throws Exception {
        send(json, buyer).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty()).andExpect(jsonPath("$.fieldErrors").isArray());
        assertCounts(0);
    }

    @Test
    void blankRequirementsHasFieldError() throws Exception {
        send(mapper.writeValueAsString(Map.of("productId", Long.toString(product), "expectedProductVersion", 0,
                "requirements", " ", "requestKey", key)), buyer).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("requirements"));
        assertCounts(0);
    }

    @Test
    void ownHiddenStoppedAndChangedProductsAreRejected() throws Exception {
        send(body(), seller).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_PURCHASE_NOT_ALLOWED"));
        jdbc.update("UPDATE products SET is_hidden=true WHERE id=?", product);
        send(body(), buyer).andExpect(status().isNotFound());
        jdbc.update("UPDATE products SET is_hidden=false,sale_status='STOPPED' WHERE id=?", product);
        send(body(), buyer).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_ORDERABLE"));
        jdbc.update("UPDATE products SET sale_status='ON_SALE',version=1 WHERE id=?", product);
        send(body(), buyer).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_VERSION_CONFLICT"));
        assertCounts(0);
    }

    @Test
    void responseAssemblyFailureRollsBackAndSameKeyCanRetry() throws Exception {
        doThrow(new IllegalStateException("simulated projection failure")).when(details).forParticipant(anyLong(), anyLong());
        send(body(), buyer).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        assertCounts(0);
        reset(details);
        send(body(), buyer).andExpect(status().isCreated());
        assertCounts(1);
    }

    @Test
    void simultaneousHttpReplayCreatesOnlyOneOrder() throws Exception {
        String request = body();
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> call = () -> {
                ready.countDown();
                if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return send(request, buyer).andReturn().getResponse().getStatus();
            };
            var first = pool.submit(call);
            var second = pool.submit(call);
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(first.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(20, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 200);
        } finally {
            start.countDown();
        }
        assertCounts(1);
    }

    @Test
    void detailProjectionDeniesNonParticipantWithoutDisclosingExistence() throws Exception {
        long id = Long.parseLong(mapper.readTree(send(body(), buyer).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString()).get("id").asString());
        assertThatThrownBy(() -> details.forParticipant(id, Long.MAX_VALUE))
                .isInstanceOfSatisfying(com.example.lancerpick.common.exception.BusinessException.class,
                        e -> assertThat(e.status().value()).isEqualTo(404));
        assertThatThrownBy(() -> details.forParticipant(Long.MAX_VALUE, buyer))
                .isInstanceOfSatisfying(com.example.lancerpick.common.exception.BusinessException.class,
                        e -> assertThat(e.status().value()).isEqualTo(404));
    }

    private String body() {
        return mapper.writeValueAsString(Map.of("productId", Long.toString(product), "expectedProductVersion", 0,
                "requirements", "　요구사항\r\n다음 줄 ", "requestKey", key));
    }
    private ResultActions send(String json, long member) throws Exception {
        return mvc.perform(post("/api/orders").with(csrf()).with(authentication(auth(member)))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
    private static UsernamePasswordAuthenticationToken auth(long member) {
        return UsernamePasswordAuthenticationToken.authenticated(new Principal(member), null, AuthorityUtils.createAuthorityList("ROLE_USER"));
    }
    private void assertCounts(long count) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE product_id=?", Long.class, product)).isEqualTo(count);
        for (String table : new String[]{"order_snapshots", "order_histories"}) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE order_id IN (SELECT id FROM orders WHERE product_id=?)",
                    Long.class, product)).isEqualTo(count);
        }
    }
    private long member(String name) {
        return insert("INSERT INTO members(email,password_hash,display_name,created_at,updated_at) VALUES (?,'fixture',?,?,?)",
                UUID.randomUUID() + "@order-api.test", name, AT, AT);
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
    static class FixedClock {
        @Bean @Primary Clock orderApiClock() {
            return Clock.fixed(Instant.parse("2026-11-02T06:00:00.123456789Z"), ZoneId.of("Asia/Seoul"));
        }
    }
}
