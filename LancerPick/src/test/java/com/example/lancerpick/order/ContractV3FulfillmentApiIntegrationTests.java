package com.example.lancerpick.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderSnapshot;
import com.example.lancerpick.order.entity.OrderTradeTerms;
import com.example.lancerpick.order.repository.OrderRepository;
import com.example.lancerpick.order.repository.OrderSnapshotRepository;
import com.example.lancerpick.order.repository.OrderTradeTermsRepository;
import com.example.lancerpick.payment.entity.OrderFunds;
import com.example.lancerpick.payment.entity.Payment;
import com.example.lancerpick.payment.repository.OrderFundsRepository;
import com.example.lancerpick.payment.repository.PaymentRepository;
import com.example.lancerpick.support.IntegrationTest;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * E6 앞부분: 금액이 움직이지 않는 V3 결제 후 명령(납품·수정 요청·재납품). V3 전액 결제(J4)가 아직 없어 결제 후 상태·결제·보관금은
 * fixture로 만든다. 구매 확정·결제 후 취소는 J4 연결 전까지 계속 막힌다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(ContractV3FulfillmentApiIntegrationTests.FixedClock.class)
class ContractV3FulfillmentApiIntegrationTests {
  private static final LocalDateTime PAID = LocalDateTime.parse("2026-11-01T00:00:00");
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper mapper;
  @Autowired OrderRepository orders;
  @Autowired OrderSnapshotRepository snapshots;
  @Autowired OrderTradeTermsRepository terms;
  @Autowired PaymentRepository payments;
  @Autowired OrderFundsRepository funds;
  long buyer, seller, category, product;
  final List<Long> created = new java.util.ArrayList<>();

  record Principal(Long memberId) implements AuthenticatedMember {}

  @BeforeEach
  void fixtures() {
    buyer = member();
    seller = member();
    category =
        insert(
            "INSERT INTO categories(name,display_order) VALUES (?,0)", "v3-" + UUID.randomUUID());
    product =
        insert(
            """
            INSERT INTO products(seller_id,category_id,title,description,scope_description,preparation_description,
                price,duration_days,created_at,updated_at) VALUES (?,?,'title','description','scope','prepare',100000,3,?,?)
            """,
            seller,
            category,
            PAID,
            PAID);
  }

  @AfterEach
  void cleanup() {
    for (long order : created) {
      for (String table :
          List.of(
              "notifications",
              "order_funds",
              "revision_requests",
              "deliveries",
              "payments",
              "order_histories",
              "order_snapshots",
              "order_trade_terms"))
        jdbc.update("DELETE FROM " + table + " WHERE order_id=?", order);
      jdbc.update(
          "DELETE FROM outbox_events WHERE aggregate_type='ORDER' AND aggregate_id=?", order);
      jdbc.update("DELETE FROM orders WHERE id=?", order);
    }
    jdbc.update("DELETE FROM products WHERE id=?", product);
    jdbc.update("DELETE FROM categories WHERE id=?", category);
    jdbc.update("DELETE FROM members WHERE id IN (?,?)", buyer, seller);
  }

  @Test
  void v3DeliveryRevisionAndRedeliveryUseExistingRulesWithoutMovingMoney() throws Exception {
    long order = paidOrder(TradePolicyVersion.CONTRACT_V3, true);
    detail(order, seller)
        .andExpect(jsonPath("$.allowedActions").value(Matchers.contains("DELIVER")));
    detail(order, buyer).andExpect(jsonPath("$.allowedActions").isEmpty());

    deliver(order, 1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("AWAITING_CONFIRMATION"))
        .andExpect(jsonPath("$.allowedActions").isEmpty());
    detail(order, buyer)
        .andExpect(jsonPath("$.allowedActions").value(Matchers.contains("REQUEST_REVISION")));

    long delivery =
        jdbc.queryForObject(
            "SELECT id FROM deliveries WHERE order_id=? AND sequence_no=1", Long.class, order);
    revise(order, delivery)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REVISION_IN_PROGRESS"))
        // 재납품 기한 = 수정 요청 시각 + 원래 소요일(3일)
        .andExpect(jsonPath("$.revisionRequest.dueAt").value("2026-11-06T02:00:00.123456Z"))
        .andExpect(jsonPath("$.allowedActions").isEmpty());
    detail(order, seller)
        .andExpect(jsonPath("$.allowedActions").value(Matchers.contains("DELIVER")));

    deliver(order, 2)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("AWAITING_CONFIRMATION"));
    // 수정은 1회뿐이고 구매 확정은 J4 전까지 열지 않는다.
    detail(order, buyer).andExpect(jsonPath("$.allowedActions").isEmpty());
    revise(order, delivery)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REVISION_ALREADY_USED"));

    assertThat(
            jdbc.queryForList(
                "SELECT action FROM order_histories WHERE order_id=? ORDER BY id", String.class, order))
        .containsExactly("DELIVERED", "REVISION_REQUESTED", "DELIVERED");
    assertThat(
            jdbc.queryForObject(
                "SELECT CONCAT(status,':',remaining_amount) FROM order_funds WHERE order_id=?",
                String.class,
                order))
        .isEqualTo("HELD:100000");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM wallet_entries WHERE payment_id IN"
                    + " (SELECT id FROM payments WHERE order_id=?)",
                Long.class,
                order))
        .isZero();
  }

  @Test
  void v3ConfirmationAndPaidCancellationStayClosedUntilFundsCommandsExist() throws Exception {
    long order = paidOrder(TradePolicyVersion.CONTRACT_V3, true);
    deliver(order, 1).andExpect(status().isOk());
    mvc.perform(
            post("/api/orders/" + order + "/confirm")
                .with(csrf())
                .with(authentication(auth(buyer))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ORDER_POLICY_NOT_READY"));
    mvc.perform(
            post("/api/orders/" + order + "/cancel")
                .with(csrf())
                .with(authentication(auth(seller)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"취소\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ORDER_POLICY_NOT_READY"));
    assertState(order, "AWAITING_CONFIRMATION");
  }

  @Test
  void v2PaidOrderStaysBlocked() throws Exception {
    long order = paidOrder(TradePolicyVersion.CONTRACT_V2, false);
    detail(order, seller).andExpect(jsonPath("$.allowedActions").isEmpty());
    deliver(order, 1)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ORDER_POLICY_NOT_READY"));
    assertState(order, "IN_PROGRESS");
  }

  @Test
  void v3WorkWithoutHeldFundsIsIntegrityErrorAndChangesNothing() throws Exception {
    long order = paidOrder(TradePolicyVersion.CONTRACT_V3, false);
    deliver(order, 1)
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_ERROR"));
    assertState(order, "IN_PROGRESS");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM deliveries WHERE order_id=?", Long.class, order))
        .isZero();

    long released = paidOrder(TradePolicyVersion.CONTRACT_V3, true);
    deliver(released, 1).andExpect(status().isOk());
    jdbc.update(
        "UPDATE order_funds SET status='RELEASED',remaining_amount=0,resolved_at=? WHERE order_id=?",
        PAID.plusDays(1),
        released);
    long delivery =
        jdbc.queryForObject("SELECT id FROM deliveries WHERE order_id=?", Long.class, released);
    revise(released, delivery)
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_ERROR"));
    assertState(released, "AWAITING_CONFIRMATION");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM revision_requests WHERE order_id=?", Long.class, released))
        .isZero();
  }

  /** 결제 후 작업 중 상태. V3이고 held이면 전액 결제·보관금 행을 함께 만든다. */
  private long paidOrder(TradePolicyVersion policy, boolean held) {
    var o =
        Order.create(buyer, seller, product, UUID.randomUUID(), 100000L, 3, PAID, policy);
    orders.saveAndFlush(o);
    long id = o.getId();
    created.add(id);
    snapshots.saveAndFlush(
        OrderSnapshot.create(id, 0L, "title", "description", "scope", "prepare", "조건", "요구사항"));
    terms.saveAndFlush(
        policy == TradePolicyVersion.CONTRACT_V3
            ? OrderTradeTerms.fullFunding(id)
            : OrderTradeTerms.contractV2(id));
    jdbc.update(
        """
        UPDATE orders SET status='IN_PROGRESS',accepted_at=created_at,buyer_confirmed_terms_at=created_at,
            paid_at=created_at,due_at=DATE_ADD(created_at,INTERVAL 3 DAY) WHERE id=?
        """,
        id);
    if (held) {
      var payment = payments.saveAndFlush(Payment.fullFunding(id, 100000, PAID));
      funds.saveAndFlush(OrderFunds.hold(id, payment.id(), 100000, PAID));
    }
    return id;
  }

  private ResultActions detail(long order, long actor) throws Exception {
    return mvc.perform(get("/api/orders/" + order).with(authentication(auth(actor))))
        .andExpect(status().isOk());
  }

  private ResultActions deliver(long order, int sequence) throws Exception {
    return mvc.perform(
        post("/api/orders/" + order + "/deliveries")
            .with(csrf())
            .with(authentication(auth(seller)))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                mapper.writeValueAsString(
                    Map.of(
                        "expectedSequenceNo",
                        sequence,
                        "description",
                        "done",
                        "resultUrl",
                        "https://example.com/result"))));
  }

  private ResultActions revise(long order, long delivery) throws Exception {
    return mvc.perform(
        post("/api/orders/" + order + "/revision-request")
            .with(csrf())
            .with(authentication(auth(buyer)))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                mapper.writeValueAsString(
                    Map.of("deliveryId", Long.toString(delivery), "reason", "수정 부탁"))));
  }

  private void assertState(long order, String state) {
    assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, order))
        .isEqualTo(state);
  }

  private static UsernamePasswordAuthenticationToken auth(long member) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new Principal(member), null, AuthorityUtils.createAuthorityList("ROLE_USER"));
  }

  private long member() {
    return insert(
        "INSERT INTO members(email,password_hash,display_name,created_at,updated_at) VALUES (?,'fixture','member',?,?)",
        UUID.randomUUID() + "@v3.test",
        PAID,
        PAID);
  }

  private long insert(String sql, Object... values) {
    var key = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
          return statement;
        },
        key);
    return key.getKey().longValue();
  }

  @TestConfiguration
  static class FixedClock {
    @Bean
    @Primary
    Clock v3FulfillmentClock() {
      return Clock.fixed(Instant.parse("2026-11-03T02:00:00.123456789Z"), ZoneId.of("Asia/Seoul"));
    }
  }
}
