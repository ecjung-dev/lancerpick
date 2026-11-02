package com.example.lancerpick.order;

import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.order.dto.CreateOrderRequest;
import com.example.lancerpick.order.repository.OrderHistoryRepository;
import com.example.lancerpick.order.repository.OrderRepository;
import com.example.lancerpick.order.repository.OrderCreationQueries;
import com.example.lancerpick.order.repository.OrderDetailQueries;
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
import org.junit.jupiter.params.provider.CsvSource;
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

/** 실제 명령·DB 이력·경쟁·롤백을 검사한다. 로그인 자체는 테스트 principal을 사용한다. */
@IntegrationTest
@AutoConfigureMockMvc
@Import(OrderDecisionApiIntegrationTests.FixedClock.class)
class OrderDecisionApiIntegrationTests {
    private static final LocalDateTime AT = LocalDateTime.parse("2026-11-03T01:00:00.123456");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @Autowired OrderCreationService creation;
    @MockitoSpyBean OrderHistoryRepository histories;
    @MockitoSpyBean OrderRepository orders;
    @MockitoSpyBean OrderCreationQueries source;
    @MockitoSpyBean OrderDetailQueries details;
    CreateOrderRequest creationRequest;
    long buyer, seller, third, category, product, order;
    record Principal(Long memberId) implements AuthenticatedMember {}

    @BeforeEach
    void fixtures() {
        buyer = member(); seller = member(); third = member();
        category = insert("INSERT INTO categories(name,display_order) VALUES (?,0)", "decision-" + UUID.randomUUID());
        product = insert("""
                INSERT INTO products(seller_id,category_id,title,description,scope_description,preparation_description,
                    price,duration_days,created_at,updated_at) VALUES (?,?,'title','description','scope','prepare',100000,3,?,?)
                """, seller, category, AT, AT);
        creationRequest = new CreateOrderRequest(Long.toString(product), 0L, "requirements", UUID.randomUUID().toString());
        order = creation.create(buyer, creationRequest).orderId();
    }

    @AfterEach
    void cleanup() {
        reset(histories);
        reset(orders);
        reset(source);
        reset(details);
        jdbc.update("DELETE FROM order_histories WHERE order_id=?", order);
        jdbc.update("DELETE FROM order_snapshots WHERE order_id=?", order);
        jdbc.update("DELETE FROM orders WHERE id=?", order);
        jdbc.update("DELETE FROM products WHERE id=?", product);
        jdbc.update("DELETE FROM categories WHERE id=?", category);
        jdbc.update("DELETE FROM members WHERE id IN (?,?,?)", buyer, seller, third);
    }

    @Test
    void acceptSetsUtcAcceptanceAndHistoryWithoutPaymentOrDueDate() throws Exception {
        jdbc.update("UPDATE products SET is_hidden=true,sale_status='STOPPED',version=1 WHERE id=?", product);
        command("accept", seller, null).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.acceptedAt").value("2026-11-03T01:00:00.123456Z"))
                .andExpect(jsonPath("$.paidAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.currentDueAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.histories[1].action").value("ACCEPTED"))
                .andExpect(jsonPath("$.histories[1].actorId").value(Long.toString(seller)))
                .andExpect(jsonPath("$.histories[1].fromStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.histories[1].toStatus").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.histories[1].reason").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.contains("CANCEL")));
        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(accepted_at,'%Y-%m-%dT%H:%i:%s.%f') FROM orders WHERE id=?", String.class, order))
                .isEqualTo("2026-11-03T01:00:00.123456");
        assertHistoryCount(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE order_id=?", Long.class, order)).isZero();
    }

    @Test
    void rejectPreservesNormalizedReasonWithoutAcceptanceOrCancellationTimestamp() throws Exception {
        command("reject", seller, mapper.writeValueAsString(Map.of("reason", "　자료 부족\r\n재신청 바랍니다 ")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.acceptedAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.cancelledAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.histories[1].action").value("REJECTED"))
                .andExpect(jsonPath("$.histories[1].reason").value("자료 부족\n재신청 바랍니다"))
                .andExpect(jsonPath("$.allowedActions").isEmpty());
        assertHistoryCount(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "reject"})
    void onlySellerCanDecideAndOutsiderCannotDiscoverOrder(String action) throws Exception {
        command(action, buyer, "{\"reason\":\"reason\"}").andExpect(status().isForbidden());
        command(action, third, "{\"reason\":\"reason\"}").andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/9223372036854775807/" + action).with(csrf()).with(authentication(auth(third)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"reason\"}"))
                .andExpect(status().isNotFound());
        assertRequested();
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "reject"})
    void repeatedOrOppositeDecisionIs409WithoutExtraHistory(String first) throws Exception {
        command(first, seller, "{\"reason\":\"reason\"}").andExpect(status().isOk());
        for (String action : List.of("accept", "reject")) {
            command(action, seller, "{\"reason\":\"reason\"}").andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ORDER_STATE_CONFLICT"));
        }
        assertHistoryCount(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reason\":null}", "{\"reason\":\"\"}", "{\"reason\":\"　\"}", "{\"reason\":1}"})
    void invalidReasonIs400AndLeavesOrderUntouched(String json) throws Exception {
        command("reject", seller, json).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertRequested();
    }

    @Test
    void reasonLimitCountsUnicodeCodePointsAfterNormalization() throws Exception {
        command("reject", seller, mapper.writeValueAsString(Map.of("reason", "😀".repeat(1001))))
                .andExpect(status().isBadRequest());
        assertRequested();
        command("reject", seller, mapper.writeValueAsString(Map.of("reason", "  " + "😀".repeat(1000) + "  ")))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({"accept,accept", "reject,reject", "accept,reject"})
    void concurrentCommandsHaveOneWinnerAndOneConflict(String a, String b) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var futures = List.of(a, b).stream().map(action -> pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return command(action, seller, "{\"reason\":\"reason\"}").andReturn().getResponse().getStatus();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally { start.countDown(); }
        assertHistoryCount(2);
    }

    @Test
    void realHistoryConstraintFailureRollsBackStateAndAcceptanceTimestamp() throws Exception {
        doAnswer(invocation -> {
            jdbc.update("INSERT INTO order_histories(order_id,actor_id,action,from_status,to_status,created_at) VALUES (?,0,'ACCEPTED','REQUESTED','AWAITING_PAYMENT',?)", order, AT);
            return null;
        }).when(histories).saveAndFlush(any());
        command("accept", seller, null).andExpect(status().isInternalServerError());
        assertRequested();
        reset(histories);
        command("accept", seller, null).andExpect(status().isOk());
    }

    @Test
    void sellerDecisionAndCreationReplayUseCompatibleMemberThenOrderLocks() throws Exception {
        var actorLocked = new CountDownLatch(1);
        var replayStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            // Spring Data 인터페이스 spy의 callRealMethod 대신 같은 트랜잭션에서 실제 잠금 SQL을 실행한다.
            var result = jdbc.query("SELECT id FROM members WHERE id=? FOR SHARE", (rs, row) -> rs.getLong("id"), seller)
                    .stream().findFirst();
            actorLocked.countDown();
            if (!replayStarted.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("replay timeout");
            return result;
        }).when(orders).lockDecisionActor(seller);
        doAnswer(invocation -> {
            replayStarted.countDown();
            return invocation.callRealMethod();
        }).when(source).lockParticipants(buyer, seller);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var decision = pool.submit(() -> command("accept", seller, null).andReturn().getResponse().getStatus());
            assertThat(actorLocked.await(10, TimeUnit.SECONDS)).isTrue();
            var replay = pool.submit(() -> creation.create(buyer, creationRequest));
            assertThat(decision.get(20, TimeUnit.SECONDS)).isEqualTo(200);
            var result = replay.get(20, TimeUnit.SECONDS);
            assertThat(result.orderId()).isEqualTo(order);
            assertThat(result.created()).isFalse();
        } finally { replayStarted.countDown(); }
        assertHistoryCount(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "reject"})
    void responseAssemblyFailureRollsBackDecisionAndHistory(String action) throws Exception {
        doThrow(new IllegalStateException("simulated projection failure")).when(details).forParticipant(anyLong(), anyLong());
        command(action, seller, "{\"reason\":\"reason\"}").andExpect(status().isInternalServerError());
        assertRequested();
        reset(details);
        command(action, seller, "{\"reason\":\"reason\"}").andExpect(status().isOk());
        assertHistoryCount(2);
    }

    @Test
    void securityRequiresRecognizedMemberAndCsrf() throws Exception {
        for (String action : List.of("accept", "reject")) {
            String path = "/api/orders/" + order + "/" + action;
            mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(path).with(authentication(auth(seller))).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
            mvc.perform(post(path).with(csrf()).with(authentication(UsernamePasswordAuthenticationToken.authenticated("name", null,
                            AuthorityUtils.createAuthorityList("ROLE_USER"))))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                    .andExpect(status().isUnauthorized());
        }
        assertRequested();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void invalidIdIs400(String id) throws Exception {
        mvc.perform(post("/api/orders/" + id + "/accept").with(csrf()).with(authentication(auth(seller))))
                .andExpect(status().isBadRequest());
        assertRequested();
    }

    private void assertRequested() {
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, order)).isEqualTo("REQUESTED");
        assertThat(jdbc.queryForObject("SELECT accepted_at FROM orders WHERE id=?", Object.class, order)).isNull();
        assertHistoryCount(1);
    }
    private void assertHistoryCount(long count) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_histories WHERE order_id=?", Long.class, order)).isEqualTo(count);
    }
    private ResultActions command(String action, long actor, String json) throws Exception {
        var request = post("/api/orders/" + order + "/" + action).with(csrf()).with(authentication(auth(actor)));
        if (json != null) request.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(request);
    }
    private static UsernamePasswordAuthenticationToken auth(long member) {
        return UsernamePasswordAuthenticationToken.authenticated(new Principal(member), null, AuthorityUtils.createAuthorityList("ROLE_USER"));
    }
    private long member() {
        return insert("INSERT INTO members(email,password_hash,display_name,created_at,updated_at) VALUES (?,'fixture','member',?,?)",
                UUID.randomUUID() + "@order-decision.test", AT, AT);
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
        @Bean @Primary Clock decisionClock() {
            return Clock.fixed(Instant.parse("2026-11-03T01:00:00.123456789Z"), ZoneId.of("Asia/Seoul"));
        }
    }
}
