package com.example.lancerpick.order;

import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.order.dto.CreateDeliveryRequest;
import com.example.lancerpick.order.dto.CreateOrderRequest;
import com.example.lancerpick.order.repository.OrderHistoryRepository;
import com.example.lancerpick.order.repository.OrderDetailQueries;
import com.example.lancerpick.order.repository.RevisionRequestRepository;
import com.example.lancerpick.order.service.OrderCreationService;
import com.example.lancerpick.order.service.OrderDeliveryService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 선행 결제는 fixture, 최초 납품·수정 요청·재납품은 실제 서비스로 검사한다. */
@IntegrationTest
@AutoConfigureMockMvc
@Import(OrderRevisionApiIntegrationTests.FixedClock.class)
class OrderRevisionApiIntegrationTests {
    private static final LocalDateTime AT = LocalDateTime.parse("2026-11-03T03:00:00.123456");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @Autowired OrderCreationService creation;
    @Autowired OrderDeliveryService deliveryService;
    @MockitoSpyBean RevisionRequestRepository revisions;
    @MockitoSpyBean OrderHistoryRepository histories;
    @MockitoSpyBean OrderDetailQueries details;
    long buyer, seller, third, category, product, order, delivery;
    record Principal(Long memberId) implements AuthenticatedMember {}

    @BeforeEach
    void fixtures() {
        buyer = member(); seller = member(); third = member();
        category = insert("INSERT INTO categories(name,display_order) VALUES (?,0)", "revision-" + UUID.randomUUID());
        product = insert("""
                INSERT INTO products(seller_id,category_id,title,description,scope_description,preparation_description,
                    price,duration_days,created_at,updated_at) VALUES (?,?,'title','description','scope','prepare',100000,3,?,?)
                """, seller, category, AT.minusDays(2), AT.minusDays(2));
        order = create(buyer);
        // 결제일과 수정 요청일을 달리해 최초 납기 재사용 오류도 잡는다.
        jdbc.update("UPDATE orders SET created_at=?,updated_at=? WHERE id=?", AT.minusDays(1), AT.minusDays(1), order);
        jdbc.update("UPDATE order_histories SET created_at=? WHERE order_id=?", AT.minusDays(1), order);
        jdbc.update("""
                UPDATE orders SET status='IN_PROGRESS',accepted_at=created_at,buyer_confirmed_terms_at=created_at,
                    paid_at=created_at,due_at=DATE_ADD(created_at,INTERVAL 3 DAY) WHERE id=?
                """, order);
        insert("INSERT INTO payments(order_id,amount,status,paid_at) VALUES (?,100000,'PAID',?)", order, AT.minusDays(1));
        deliveryService.submit(seller, Long.toString(order), new CreateDeliveryRequest("first", "https://example.com/result", 1));
        delivery = jdbc.queryForObject("SELECT id FROM deliveries WHERE order_id=?", Long.class, order);
    }

    @AfterEach
    void cleanup() {
        reset(revisions, histories, details);
        for (String table : List.of("cancellation_requests", "revision_requests", "deliveries", "payments", "order_histories", "order_snapshots")) {
            jdbc.update("DELETE FROM " + table + " WHERE order_id IN (SELECT id FROM orders WHERE product_id=?)", product);
        }
        jdbc.update("DELETE FROM orders WHERE product_id=?", product);
        jdbc.update("DELETE FROM products WHERE id=?", product);
        jdbc.update("DELETE FROM categories WHERE id=?", category);
        jdbc.update("DELETE FROM members WHERE id IN (?,?,?)", buyer, seller, third);
    }

    @Test
    void requestSetsStoredDurationDeadlineAndRedeliveryCompletesTheRevisionFlow() throws Exception {
        jdbc.update("UPDATE products SET duration_days=30,version=1,is_hidden=true WHERE id=?", product);
        submit(buyer, body(Long.toString(delivery), "　색상 수정\r\n부탁합니다 "))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("REVISION_IN_PROGRESS"))
                .andExpect(jsonPath("$.revisionRequest.deliveryId").value(Long.toString(delivery)))
                .andExpect(jsonPath("$.revisionRequest.reason").value("색상 수정\n부탁합니다"))
                .andExpect(jsonPath("$.revisionRequest.requestedAt").value("2026-11-03T03:00:00.123456Z"))
                .andExpect(jsonPath("$.revisionDueAt").value("2026-11-06T03:00:00.123456Z"))
                .andExpect(jsonPath("$.originalDueAt").value("2026-11-05T03:00:00.123456Z"))
                .andExpect(jsonPath("$.durationDays").value(3))
                .andExpect(jsonPath("$.histories[2].action").value("REVISION_REQUESTED"))
                .andExpect(jsonPath("$.histories[2].actorId").value(Long.toString(buyer)))
                .andExpect(jsonPath("$.histories[2].fromStatus").value("AWAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.histories[2].toStatus").value("REVISION_IN_PROGRESS"))
                .andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.contains("REQUEST_CANCELLATION")));
        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(requested_at,'%Y-%m-%dT%H:%i:%s.%f') FROM revision_requests WHERE order_id=?", String.class, order))
                .isEqualTo("2026-11-03T03:00:00.123456");
        submit(buyer, valid()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVISION_ALREADY_USED"));
        deliveryService.submit(seller, Long.toString(order), new CreateDeliveryRequest("revised", "https://example.com/revised", 2));
        mvc.perform(get("/api/orders/" + order).with(authentication(auth(buyer)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.containsInAnyOrder("REQUEST_CANCELLATION", "CONFIRM")));
        submit(buyer, valid()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVISION_ALREADY_USED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deliveries WHERE order_id=?", Long.class, order)).isEqualTo(2);
        assertCounts(1, 4);
    }

    @Test
    void onlyBuyerCanRequestAndForeignDeliveryIs404() throws Exception {
        submit(seller, valid()).andExpect(status().isForbidden());
        submit(third, valid()).andExpect(status().isNotFound());
        long other = create(third);
        long foreignDelivery = insert("INSERT INTO deliveries(order_id,sequence_no,description,result_url,submitted_at) VALUES (?,1,'foreign','https://example.com',?)", other, AT);
        submit(buyer, body(Long.toString(foreignDelivery), "reason")).andExpect(status().isNotFound());
        submit(buyer, body("9223372036854775807", "reason")).andExpect(status().isNotFound());
        assertUnchanged();
    }

    @Test
    void pendingCancellationBlocksButResolvedRequestDoesNot() throws Exception {
        long cancellation = insert("INSERT INTO cancellation_requests(order_id,requested_by,request_key,reason,requested_at) VALUES (?,?,?,'cancel',?)", order, buyer, UUID.randomUUID().toString(), AT);
        submit(buyer, valid()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANCELLATION_PENDING"));
        assertUnchanged();
        jdbc.update("UPDATE cancellation_requests SET status='WITHDRAWN',resolved_at=requested_at,resolved_by=? WHERE id=?", buyer, cancellation);
        submit(buyer, valid()).andExpect(status().isOk());
    }

    @Test
    void nonConfirmationStateIs409() throws Exception {
        jdbc.update("UPDATE orders SET status='IN_PROGRESS' WHERE id=?", order);
        submit(buyer, valid()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_STATE_CONFLICT"));
        assertCounts(0, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"deliveryId\":1,\"reason\":\"x\"}", "{\"deliveryId\":\"1\",\"reason\":1}",
            "{\"deliveryId\":\"0\",\"reason\":\"x\"}", "{\"deliveryId\":\"9223372036854775808\",\"reason\":\"x\"}"})
    void invalidTypesAndDeliveryIdsAre400(String json) throws Exception {
        submit(buyer, json).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertUnchanged();
    }

    @Test
    void reasonUsesNormalizedCodePointLimit() throws Exception {
        submit(buyer, body(Long.toString(delivery), "　")).andExpect(status().isBadRequest());
        submit(buyer, body(Long.toString(delivery), "😀".repeat(1001))).andExpect(status().isBadRequest());
        assertUnchanged();
        submit(buyer, body(Long.toString(delivery), " " + "😀".repeat(1000) + " ")).andExpect(status().isOk());
    }

    @Test
    void anonymousUnrecognizedPrincipalAndCsrfAreRejected() throws Exception {
        mvc.perform(post(path()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(valid())).andExpect(status().isUnauthorized());
        mvc.perform(post(path()).with(authentication(auth(buyer))).contentType(MediaType.APPLICATION_JSON).content(valid()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(post(path()).with(csrf()).with(authentication(UsernamePasswordAuthenticationToken.authenticated("name", null,
                        AuthorityUtils.createAuthorityList("ROLE_USER")))).contentType(MediaType.APPLICATION_JSON).content(valid()))
                .andExpect(status().isUnauthorized());
        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "bad", "9223372036854775808"})
    void invalidOrderIdsAre400(String id) throws Exception {
        mvc.perform(post("/api/orders/" + id + "/revision-request").with(csrf()).with(authentication(auth(buyer)))
                .contentType(MediaType.APPLICATION_JSON).content(valid())).andExpect(status().isBadRequest());
        assertUnchanged();
    }

    @Test
    void concurrentRequestsHaveOneWinnerAndPreserveOneDeadline() throws Exception {
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1); String request = valid();
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> call = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return submit(buyer, request).andReturn().getResponse().getStatus();
            };
            var a = pool.submit(call); var b = pool.submit(call);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        } finally { start.countDown(); }
        assertCounts(1, 3);
    }

    @Test
    void historyFkFailureRollsBackRequestStateAndDeadlineAndCanRetry() throws Exception {
        doAnswer(invocation -> {
            jdbc.update("INSERT INTO order_histories(order_id,actor_id,action,from_status,to_status,created_at) VALUES (?,0,'REVISION_REQUESTED','AWAITING_CONFIRMATION','REVISION_IN_PROGRESS',?)", order, AT);
            return null;
        }).when(histories).saveAndFlush(any());
        submit(buyer, valid()).andExpect(status().isInternalServerError());
        assertUnchanged(); reset(histories);
        submit(buyer, valid()).andExpect(status().isOk());
    }

    @Test
    void requestFkAndProjectionFailuresAlsoRollback() throws Exception {
        doAnswer(invocation -> {
            jdbc.update("INSERT INTO revision_requests(order_id,delivery_id,reason,requested_at,due_at) VALUES (?,0,'bad',?,?)", order, AT, AT.plusDays(3));
            return null;
        }).when(revisions).saveAndFlush(any());
        submit(buyer, valid()).andExpect(status().isInternalServerError());
        assertUnchanged(); reset(revisions);
        doThrow(new IllegalStateException("projection failure")).when(details).forParticipant(anyLong(), anyLong());
        submit(buyer, valid()).andExpect(status().isInternalServerError());
        assertUnchanged(); reset(details);
        submit(buyer, valid()).andExpect(status().isOk());
    }

    private long create(long buyer) {
        return creation.create(buyer, new CreateOrderRequest(Long.toString(product), 0L, "requirements", UUID.randomUUID().toString())).orderId();
    }
    private String valid() { return body(Long.toString(delivery), "reason"); }
    private String body(String delivery, String reason) { return mapper.writeValueAsString(Map.of("deliveryId", delivery, "reason", reason)); }
    private String path() { return "/api/orders/" + order + "/revision-request"; }
    private ResultActions submit(long actor, String body) throws Exception {
        return mvc.perform(post(path()).with(csrf()).with(authentication(auth(actor))).contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private static UsernamePasswordAuthenticationToken auth(long id) {
        return UsernamePasswordAuthenticationToken.authenticated(new Principal(id), null, AuthorityUtils.createAuthorityList("ROLE_USER"));
    }
    private void assertUnchanged() {
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, order)).isEqualTo("AWAITING_CONFIRMATION");
        assertCounts(0, 2);
    }
    private void assertCounts(long requests, long history) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM revision_requests WHERE order_id=?", Long.class, order)).isEqualTo(requests);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_histories WHERE order_id=?", Long.class, order)).isEqualTo(history);
    }
    private long member() { return insert("INSERT INTO members(email,password_hash,display_name,created_at,updated_at) VALUES (?,'fixture','member',?,?)", UUID.randomUUID() + "@revision.test", AT.minusDays(2), AT.minusDays(2)); }
    private long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return key.getKey().longValue();
    }
    @TestConfiguration
    static class FixedClock {
        @Bean @Primary Clock revisionClock() { return Clock.fixed(Instant.parse("2026-11-03T03:00:00.123456789Z"), ZoneId.of("America/Los_Angeles")); }
    }
}
