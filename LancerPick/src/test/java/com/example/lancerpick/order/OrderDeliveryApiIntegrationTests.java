package com.example.lancerpick.order;

import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.order.dto.CreateOrderRequest;
import com.example.lancerpick.order.entity.Delivery;
import com.example.lancerpick.order.repository.DeliveryRepository;
import com.example.lancerpick.order.repository.OrderDetailQueries;
import com.example.lancerpick.order.repository.OrderHistoryRepository;
import com.example.lancerpick.order.service.OrderCreationService;
import com.example.lancerpick.support.IntegrationTest;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 결제·수정 요청은 SQL fixture로 구성하고 실제 HTTP 납품·상태·이력·롤백을 검증한다. */
@IntegrationTest
@AutoConfigureMockMvc
@Import(OrderDeliveryApiIntegrationTests.FixedClock.class)
class OrderDeliveryApiIntegrationTests {
    private static final LocalDateTime AT = LocalDateTime.parse("2026-11-03T02:00:00.123456");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @Autowired OrderCreationService creation;
    @MockitoSpyBean DeliveryRepository deliveries;
    @MockitoSpyBean OrderHistoryRepository histories;
    @MockitoSpyBean OrderDetailQueries details;
    long buyer, seller, third, category, product, order;
    record Principal(Long memberId) implements AuthenticatedMember {}

    @BeforeEach
    void fixtures() {
        buyer = member(); seller = member(); third = member();
        category = insert("INSERT INTO categories(name,display_order) VALUES (?,0)", "delivery-" + UUID.randomUUID());
        product = insert("""
                INSERT INTO products(seller_id,category_id,title,description,scope_description,preparation_description,
                    price,duration_days,created_at,updated_at) VALUES (?,?,'title','description','scope','prepare',100000,3,?,?)
                """, seller, category, AT, AT);
        order = creation.create(buyer, new CreateOrderRequest(Long.toString(product), 0L, "requirements", UUID.randomUUID().toString())).orderId();
        jdbc.update("""
                UPDATE orders SET status='IN_PROGRESS',accepted_at=created_at,buyer_confirmed_terms_at=created_at,
                    paid_at=created_at,due_at=DATE_ADD(created_at,INTERVAL 3 DAY) WHERE id=?
                """, order);
        insert("INSERT INTO payments(order_id,amount,status,paid_at) VALUES (?,100000,'PAID',?)", order, AT);
    }

    @AfterEach
    void cleanup() {
        reset(deliveries, histories, details);
        for (String table : List.of("cancellation_requests", "revision_requests", "deliveries", "payments", "order_histories", "order_snapshots"))
            jdbc.update("DELETE FROM " + table + " WHERE order_id=?", order);
        jdbc.update("DELETE FROM orders WHERE id=?", order);
        jdbc.update("DELETE FROM products WHERE id=?", product);
        jdbc.update("DELETE FROM categories WHERE id=?", category);
        jdbc.update("DELETE FROM members WHERE id IN (?,?,?)", buyer, seller, third);
    }

    @Test
    void firstDeliveryPersistsNormalizedContentSharedUtcAndKeepsOriginalDueDate() throws Exception {
        submit(seller, body(1, "　완료\r\n확인해 주세요 ", " https://example.com/result "))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("AWAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.deliveries[0].sequenceNo").value(1))
                .andExpect(jsonPath("$.deliveries[0].description").value("완료\n확인해 주세요"))
                .andExpect(jsonPath("$.deliveries[0].resultUrl").value("https://example.com/result"))
                .andExpect(jsonPath("$.deliveries[0].submittedAt").value("2026-11-03T02:00:00.123456Z"))
                .andExpect(jsonPath("$.currentDueAt").value("2026-11-06T02:00:00.123456Z"))
                .andExpect(jsonPath("$.histories[1].action").value("DELIVERED"))
                .andExpect(jsonPath("$.histories[1].fromStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.histories[1].toStatus").value("AWAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.histories[1].actorId").value(Long.toString(seller)))
                .andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.contains("CANCEL")));
        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(submitted_at,'%Y-%m-%dT%H:%i:%s.%f') FROM deliveries WHERE order_id=?", String.class, order))
                .isEqualTo("2026-11-03T02:00:00.123456");
        assertCounts(1, 2);
    }

    @Test
    void oldRoundReplayDuringRevisionDoesNotBecomeSecondDelivery() throws Exception {
        submit(seller, valid(1)).andExpect(status().isOk());
        revision();
        submit(seller, valid(1)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DELIVERY_ALREADY_SUBMITTED"));
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, order)).isEqualTo("REVISION_IN_PROGRESS");
        assertCounts(1, 2);
        submit(seller, valid(2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveries[1].sequenceNo").value(2))
                .andExpect(jsonPath("$.histories[2].fromStatus").value("REVISION_IN_PROGRESS"))
                .andExpect(jsonPath("$.currentDueAt").value("2026-11-07T02:00:00.123456Z"));
        submit(seller, valid(2)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DELIVERY_ALREADY_SUBMITTED"));
        assertCounts(2, 3);
    }

    @Test
    void wrongSequenceAndPendingCancellationCreateNothing() throws Exception {
        submit(seller, valid(2)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DELIVERY_SEQUENCE_CONFLICT"));
        insert("INSERT INTO cancellation_requests(order_id,requested_by,request_key,reason,requested_at) VALUES (?,?,?,'cancel',?)", order, buyer, UUID.randomUUID().toString(), AT);
        submit(seller, valid(1)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANCELLATION_PENDING"));
        assertCounts(0, 1);
        assertState("IN_PROGRESS");
    }

    @Test
    void revisionStateWithoutMatchingFirstDeliveryAndRequestIsIntegrityError() throws Exception {
        jdbc.update("UPDATE orders SET status='REVISION_IN_PROGRESS' WHERE id=?", order);
        submit(seller, valid(2)).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("DATA_INTEGRITY_ERROR"));
        assertCounts(0, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com/result", "https:///missing", "https://user:secret@example.com/result", "https://example.com/a b", "javascript:alert(1)"})
    void invalidUrlIs400WithoutSubmission(String url) throws Exception {
        submit(seller, body(1, "done", url)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_URL"));
        assertCounts(0, 1);
        assertState("IN_PROGRESS");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"description\":\"done\",\"resultUrl\":\"https://example.com\",\"expectedSequenceNo\":\"1\"}",
            "{\"description\":\"done\",\"resultUrl\":\"https://example.com\",\"expectedSequenceNo\":1.0}",
            "{\"description\":1,\"resultUrl\":\"https://example.com\",\"expectedSequenceNo\":1}",
            "{\"description\":\"done\",\"resultUrl\":\"https://example.com\",\"expectedSequenceNo\":3}",
            "{\"description\":\"done\",\"resultUrl\":\"https://example.com\",\"expectedSequenceNo\":2147483648}"})
    void invalidTypesAndSequenceAre400(String json) throws Exception {
        submit(seller, json).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertCounts(0, 1);
    }

    @Test
    void descriptionUsesNormalizedCodePointLength() throws Exception {
        submit(seller, body(1, "😀".repeat(5001), "https://example.com")).andExpect(status().isBadRequest());
        submit(seller, body(1, "　", "https://example.com")).andExpect(status().isBadRequest());
        assertCounts(0, 1);
        submit(seller, body(1, " " + "😀".repeat(5000) + " ", "https://example.com")).andExpect(status().isOk());
    }

    @Test
    void onlySellerCanSubmitAndCsrfIsRequired() throws Exception {
        submit(buyer, valid(1)).andExpect(status().isForbidden());
        submit(third, valid(1)).andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/9223372036854775807/deliveries").with(csrf()).with(authentication(auth(third)))
                        .contentType(MediaType.APPLICATION_JSON).content(valid(1))).andExpect(status().isNotFound());
        mvc.perform(post(path()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(valid(1))).andExpect(status().isUnauthorized());
        mvc.perform(post(path()).with(authentication(auth(seller))).contentType(MediaType.APPLICATION_JSON).content(valid(1)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertCounts(0, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "bad", "9223372036854775808"})
    void invalidOrderIdIs400(String id) throws Exception {
        mvc.perform(post("/api/orders/" + id + "/deliveries").with(csrf()).with(authentication(auth(seller)))
                        .contentType(MediaType.APPLICATION_JSON).content(valid(1))).andExpect(status().isBadRequest());
        assertState("IN_PROGRESS"); assertCounts(0, 1);
    }

    @Test
    void concurrentSameRoundHasOneWinner() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        String request = valid(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> task = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return submit(seller, request).andReturn().getResponse().getStatus();
            };
            var a = pool.submit(task); var b = pool.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        } finally { start.countDown(); }
        assertCounts(1, 2);
    }

    @Test
    void historyFailureRollsBackDeliveryAndOrderThenAllowsRetry() throws Exception {
        doAnswer(invocation -> {
            jdbc.update("INSERT INTO order_histories(order_id,actor_id,action,from_status,to_status,created_at) VALUES (?,0,'DELIVERED','IN_PROGRESS','AWAITING_CONFIRMATION',?)", order, AT);
            return null;
        }).when(histories).saveAndFlush(any());
        submit(seller, valid(1)).andExpect(status().isInternalServerError());
        assertState("IN_PROGRESS"); assertCounts(0, 1);
        reset(histories);
        submit(seller, valid(1)).andExpect(status().isOk());
    }

    @Test
    void deliveryConstraintFailureAndProjectionFailureAlsoRollback() throws Exception {
        doAnswer(invocation -> {
            jdbc.update("INSERT INTO deliveries(order_id,sequence_no,description,result_url,submitted_at) VALUES (?,3,'bad','https://example.com',?)", order, AT);
            return null;
        }).when(deliveries).saveAndFlush(any(Delivery.class));
        submit(seller, valid(1)).andExpect(status().isInternalServerError());
        assertState("IN_PROGRESS"); assertCounts(0, 1);
        reset(deliveries);
        doThrow(new IllegalStateException("projection failure")).when(details).forParticipant(anyLong(), anyLong());
        submit(seller, valid(1)).andExpect(status().isInternalServerError());
        assertState("IN_PROGRESS"); assertCounts(0, 1);
        reset(details);
        submit(seller, valid(1)).andExpect(status().isOk());
    }

    private void revision() {
        long delivery = jdbc.queryForObject("SELECT id FROM deliveries WHERE order_id=? AND sequence_no=1", Long.class, order);
        insert("INSERT INTO revision_requests(order_id,delivery_id,reason,requested_at,due_at) VALUES (?,?,'revise',?,?)", order, delivery, AT, AT.plusDays(4));
        jdbc.update("UPDATE orders SET status='REVISION_IN_PROGRESS' WHERE id=?", order);
    }
    private String valid(int sequence) { return body(sequence, "done", "https://example.com/result"); }
    private String body(int sequence, String text, String url) {
        return mapper.writeValueAsString(Map.of("expectedSequenceNo", sequence, "description", text, "resultUrl", url));
    }
    private String path() { return "/api/orders/" + order + "/deliveries"; }
    private ResultActions submit(long actor, String body) throws Exception {
        return mvc.perform(post(path()).with(csrf()).with(authentication(auth(actor))).contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private static UsernamePasswordAuthenticationToken auth(long member) {
        return UsernamePasswordAuthenticationToken.authenticated(new Principal(member), null, AuthorityUtils.createAuthorityList("ROLE_USER"));
    }
    private void assertState(String state) { assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, order)).isEqualTo(state); }
    private void assertCounts(long deliveryCount, long historyCount) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deliveries WHERE order_id=?", Long.class, order)).isEqualTo(deliveryCount);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_histories WHERE order_id=?", Long.class, order)).isEqualTo(historyCount);
    }
    private long member() { return insert("INSERT INTO members(email,password_hash,display_name,created_at,updated_at) VALUES (?,'fixture','member',?,?)", UUID.randomUUID() + "@delivery.test", AT, AT); }
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
        @Bean @Primary Clock deliveryClock() { return Clock.fixed(Instant.parse("2026-11-03T02:00:00.123456789Z"), ZoneId.of("Asia/Seoul")); }
    }
}
