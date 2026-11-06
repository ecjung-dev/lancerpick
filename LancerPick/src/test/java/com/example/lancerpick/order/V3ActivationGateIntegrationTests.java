package com.example.lancerpick.order;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.contract.service.ContractDocumentService;
import com.example.lancerpick.contract.service.OrderContractService;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.order.dto.CreateCancellationRequest;
import com.example.lancerpick.order.dto.CreateDeliveryRequest;
import com.example.lancerpick.order.dto.CreateOrderRequest;
import com.example.lancerpick.order.dto.CreateRevisionRequest;
import com.example.lancerpick.order.service.OrderCancellationRequestService;
import com.example.lancerpick.order.service.OrderCancellationService;
import com.example.lancerpick.order.service.OrderConfirmationService;
import com.example.lancerpick.order.service.OrderCreationService;
import com.example.lancerpick.order.service.OrderDecisionService;
import com.example.lancerpick.order.service.OrderDeliveryService;
import com.example.lancerpick.order.service.OrderPaymentService;
import com.example.lancerpick.order.service.OrderRevisionService;
import com.example.lancerpick.order.service.ReviewTimerService;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.support.V3FundsInvariant;
import com.example.lancerpick.wallet.entity.Wallet;
import com.example.lancerpick.wallet.entity.WalletEntry;
import com.example.lancerpick.wallet.repository.WalletEntryRepository;
import com.example.lancerpick.wallet.repository.WalletRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G1 활성화 게이트 추가 검사(docs/v3-activation-gate.md 3절). 실제 설정 {@code
 * lancerpick.order.full-funding-creation-enabled=true}로 신규 주문을 만들고, 실제 커밋·잠금·PDF로 검사한다. 48시간·7일
 * 경과는 납품·타이머 시각을 같은 폭으로 과거로 옮겨 재현한다(DB CHECK의 시각 관계 유지). 모든 검사 뒤 금액 불변식을 확인한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class V3ActivationGateIntegrationTests {
  static final long AMOUNT = 600001;
  static final long FEE = 60000;
  static final long PAYOUT = AMOUNT - FEE;
  static final long GRANT = 1000000;
  static final Path directory = temp();

  static Path temp() {
    try {
      return Files.createTempDirectory("lancerpick-v3-gate-");
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry p) {
    p.add("contracts.storage-root", directory::toString);
    p.add("lancerpick.order.full-funding-creation-enabled", () -> "true");
  }

  @Autowired MemberRepository members;
  @Autowired WalletRepository wallets;
  @Autowired WalletEntryRepository entries;
  @Autowired OrderCreationService creations;
  @Autowired OrderDecisionService decisions;
  @Autowired OrderContractService contracts;
  @Autowired ContractDocumentService documents;
  @Autowired OrderPaymentService payments;
  @Autowired OrderConfirmationService confirmations;
  @Autowired OrderDeliveryService deliveries;
  @Autowired OrderRevisionService revisions;
  @Autowired OrderCancellationRequestService requests;
  @Autowired OrderCancellationService cancellations;
  @Autowired ReviewTimerService timers;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @Autowired MockMvc mvc;
  long buyer, seller, admin, product, category;
  final List<Long> ids = new ArrayList<>();

  record Principal(Long memberId) implements AuthenticatedMember {}

  UsernamePasswordAuthenticationToken auth(long id) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new Principal(id), null, AuthorityUtils.createAuthorityList("ROLE_USER"));
  }

  @BeforeEach
  void fixture() {
    new TransactionTemplate(manager)
        .executeWithoutResult(ignored -> {
          var at = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1).truncatedTo(ChronoUnit.MICROS);
          buyer = member(at);
          seller = member(at);
          admin = member(at);
          jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", admin);
          String name = "v3-gate-" + UUID.randomUUID();
          jdbc.update("INSERT INTO categories(name,display_order) VALUES (?,0)", name);
          category =
              jdbc.queryForObject("SELECT id FROM categories WHERE name=?", Long.class, name);
          jdbc.update(
              "INSERT INTO"
                  + " products(seller_id,category_id,title,description,scope_description,preparation_description,price,duration_days,created_at,updated_at)"
                  + " VALUES (?,?,'상품','설명','범위','준비',?,1,?,?)",
              seller,
              category,
              AMOUNT,
              at,
              at);
          product =
              jdbc.queryForObject("SELECT id FROM products WHERE seller_id=?", Long.class, seller);
        });
  }

  long member(LocalDateTime at) {
    long m =
        members
            .saveAndFlush(Member.create(UUID.randomUUID() + "@gate.test", "fixture", "표시 이름", at))
            .getId();
    wallets.saveAndFlush(Wallet.withInitialGrant(m, at));
    entries.saveAndFlush(WalletEntry.initialGrant(m, at));
    return m;
  }

  @AfterEach
  void cleanup() throws Exception {
    // 지우기 전에 불변식을 확인하고, 정리는 항상 끝낸 뒤 판정한다.
    var violations = V3FundsInvariant.violations(jdbc, ids);
    for (long order : ids) {
      var keys =
          jdbc.queryForList(
              "SELECT file_storage_key FROM order_contracts WHERE order_id=? AND file_storage_key"
                  + " IS NOT NULL",
              String.class,
              order);
      jdbc.update("DELETE FROM notifications WHERE order_id=?", order);
      jdbc.update(
          "DELETE FROM outbox_events WHERE aggregate_type='ORDER' AND aggregate_id=?", order);
      for (String table :
          List.of("order_histories", "order_closures", "order_funds", "settlements"))
        jdbc.update("DELETE FROM " + table + " WHERE order_id=?", order);
      jdbc.update(
          "DELETE FROM wallet_entries WHERE payment_id IN (SELECT id FROM payments WHERE"
              + " order_id=?)",
          order);
      for (String table :
          List.of(
              "payments",
              "cancellation_requests",
              "order_response_timers",
              "revision_requests",
              "deliveries")) jdbc.update("DELETE FROM " + table + " WHERE order_id=?", order);
      jdbc.update(
          "DELETE FROM contract_signatures WHERE contract_id IN (SELECT id FROM order_contracts"
              + " WHERE order_id=?)",
          order);
      for (String table : List.of("order_contracts", "order_trade_terms", "order_snapshots"))
        jdbc.update("DELETE FROM " + table + " WHERE order_id=?", order);
      jdbc.update("DELETE FROM orders WHERE id=?", order);
      for (String key : keys) Files.deleteIfExists(directory.resolve(key));
    }
    jdbc.update("DELETE FROM products WHERE id=?", product);
    jdbc.update("DELETE FROM categories WHERE id=?", category);
    jdbc.update("DELETE FROM wallet_entries WHERE member_id IN (?,?,?)", buyer, seller, admin);
    jdbc.update("DELETE FROM wallets WHERE member_id IN (?,?,?)", buyer, seller, admin);
    jdbc.update("DELETE FROM members WHERE id IN (?,?,?)", buyer, seller, admin);
    assertThat(violations).as("V3 금액 불변식 위반").isEmpty();
  }

  @AfterAll
  static void removeDirectory() throws Exception {
    try (var files = Files.list(directory)) {
      for (var p : files.toList()) Files.delete(p);
    }
    Files.delete(directory);
  }

  // ---------- helpers ----------

  /** 실제 생성 경로(설정 on)로 주문을 만든다. */
  long requested() {
    long id =
        creations
            .create(
                buyer,
                new CreateOrderRequest(
                    Long.toString(product), 0L, "요구사항", UUID.randomUUID().toString()))
            .orderId();
    ids.add(id);
    return id;
  }

  long accepted() {
    long id = requested();
    decisions.accept(seller, Long.toString(id));
    return id;
  }

  long signed() {
    long id = accepted();
    var draft = contracts.prepare(seller, id, 0, "");
    var ready = documents.generate(seller, id, draft.version());
    contracts.sign(buyer, id, ready.version(), ready.contentHash(), ready.fileHash());
    contracts.sign(seller, id, ready.version(), ready.contentHash(), ready.fileHash());
    return id;
  }

  long delivered() {
    long id = signed();
    payments.pay(buyer, Long.toString(id), true);
    deliveries.submit(
        seller,
        Long.toString(id),
        new CreateDeliveryRequest("납품", "https://example.invalid/result", 1));
    return id;
  }

  /** 납품 후 48시간이 지난 상태에서 판매자가 7일 타이머를 시작한다. */
  long timerActive() {
    long id = delivered();
    jdbc.update(
        "UPDATE deliveries SET submitted_at=submitted_at - INTERVAL 49 HOUR WHERE order_id=?", id);
    assertThat(timers.start(seller, id).status()).isEqualTo("ACTIVE");
    return id;
  }

  /** 상태와 관계없이 주문의 타이머 시각을 같은 폭으로 옮겨 만료 시각을 지나게 한다. */
  void shiftTimersPastExpiry(long id) {
    jdbc.update(
        "UPDATE order_response_timers SET basis_at=basis_at - INTERVAL 169 HOUR,"
            + " eligible_at=eligible_at - INTERVAL 169 HOUR, started_at=started_at - INTERVAL 169"
            + " HOUR, expires_at=expires_at - INTERVAL 169 HOUR WHERE order_id=?",
        id);
  }

  long balance(long m) {
    return wallets.findById(m).orElseThrow().getBalance();
  }

  long count(String table, long id) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE order_id=?", Long.class, id);
  }

  String orderStatus(long id) {
    return jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id);
  }

  String timerStatus(long id) {
    return jdbc.queryForObject(
        "SELECT status FROM order_response_timers WHERE order_id=? ORDER BY id DESC LIMIT 1",
        String.class,
        id);
  }

  long deliveryId(long id) {
    return jdbc.queryForObject(
        "SELECT id FROM deliveries WHERE order_id=? AND sequence_no=1", Long.class, id);
  }

  long moneyRows(long id) {
    return count("payments", id)
        + count("order_funds", id)
        + count("order_closures", id)
        + count("settlements", id);
  }

  static String result(Runnable action) {
    try {
      action.run();
      return "OK";
    } catch (BusinessException e) {
      return e.code();
    }
  }

  @SafeVarargs
  static List<String> parallel(Callable<String>... jobs) throws Exception {
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(jobs.length)) {
      var futures = new ArrayList<Future<String>>();
      for (var job : jobs)
        futures.add(
            pool.submit(() -> {
              gate.await();
              return job.call();
            }));
      gate.countDown();
      var values = new ArrayList<String>();
      for (var f : futures) values.add(f.get(30, TimeUnit.SECONDS));
      return values;
    }
  }

  MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, long actor, String body) {
    return request
        .with(authentication(auth(actor)))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  String postOk(String path, long actor, String body) throws Exception {
    return mvc.perform(json(post(path), actor, body))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  // ---------- 불변식 검사 자체 ----------

  @Test
  void invariantCheckReportsMissingDebitLedger() {
    long id = signed();
    payments.pay(buyer, Long.toString(id), true);
    assertThat(V3FundsInvariant.violations(jdbc, ids)).isEmpty();

    jdbc.update(
        "DELETE FROM wallet_entries WHERE type='PAYMENT' AND payment_id=(SELECT id FROM payments"
            + " WHERE order_id=?)",
        id);
    assertThat(V3FundsInvariant.violations(jdbc, ids))
        .singleElement()
        .satisfies(row -> assertThat(((Number) row.get("id")).longValue()).isEqualTo(id));

    // 정리 단계의 불변식 판정에 걸리지 않도록 깨뜨린 결제 기록을 지운다.
    jdbc.update("DELETE FROM order_funds WHERE order_id=?", id);
    jdbc.update("DELETE FROM payments WHERE order_id=?", id);
    assertThat(V3FundsInvariant.violations(jdbc, ids)).isEmpty();
  }

  // ---------- A1 ----------

  @Test
  void switchOnHttpTradeRunsFromCreationToConfirmationWithOneMoneyResult() throws Exception {
    String created =
        postOk(
            "/api/orders",
            buyer,
            "{\"productId\":\"%d\",\"expectedProductVersion\":0,\"requirements\":\"요구사항\",\"requestKey\":\"%s\"}"
                .formatted(product, UUID.randomUUID()));
    long id = Long.parseLong(JsonPath.read(created, "$.id"));
    ids.add(id);
    String base = "/api/orders/" + id;
    assertThat(jdbc.queryForObject("SELECT policy_version FROM orders WHERE id=?", String.class, id))
        .isEqualTo("CONTRACT_V3");
    assertThat(
            jdbc.queryForObject(
                "SELECT funding_mode FROM order_trade_terms WHERE order_id=?", String.class, id))
        .isEqualTo("FULL_HOLD");

    postOk(base + "/accept", seller, "{}");
    postOk(base + "/contract/draft", seller, "{\"expectedVersion\":0,\"specialTerms\":\"특약\"}");
    String ready = postOk(base + "/contract/generate", seller, "{\"version\":1}");
    String contentHash = JsonPath.read(ready, "$.contentHash");
    String fileHash = JsonPath.read(ready, "$.fileHash");
    assertThat(fileHash).isNotBlank();
    String sign =
        "{\"version\":1,\"contentHash\":\"%s\",\"fileHash\":\"%s\",\"agreed\":true}"
            .formatted(contentHash, fileHash);
    postOk(base + "/contract/sign", buyer, sign);
    postOk(base + "/contract/sign", seller, sign);
    assertThat(orderStatus(id)).isEqualTo("AWAITING_PAYMENT");

    mvc.perform(json(post(base + "/payment"), buyer, "{\"agreedToTerms\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    assertThat(balance(buyer)).isEqualTo(GRANT - AMOUNT);
    mvc.perform(get(base + "/funds").with(authentication(auth(seller))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("HELD"))
        .andExpect(jsonPath("$.heldAmount").value(AMOUNT));

    postOk(
        base + "/deliveries",
        seller,
        "{\"description\":\"납품\",\"resultUrl\":\"https://example.invalid/r\",\"expectedSequenceNo\":1}");
    mvc.perform(json(post(base + "/confirm"), buyer, "{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    mvc.perform(get(base + "/funds").with(authentication(auth(buyer))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RELEASED"))
        .andExpect(jsonPath("$.heldAmount").value(0))
        .andExpect(jsonPath("$.feeAmount").value(FEE))
        .andExpect(jsonPath("$.sellerPayout").value(PAYOUT));
    assertThat(balance(buyer)).isEqualTo(GRANT - AMOUNT);
    assertThat(balance(seller)).isEqualTo(GRANT + PAYOUT);
    assertThat(count("settlements", id)).isOne();
    assertThat(
            jdbc.queryForList(
                "SELECT action FROM order_histories WHERE order_id=? ORDER BY created_at, id",
                String.class,
                id))
        .containsExactly(
            "CREATED",
            "ACCEPTED",
            "CONTRACT_CREATED",
            "CONTRACT_SIGNED",
            "PAID",
            "DEADLINE_NOTIFIED",
            "DELIVERED",
            "COMPLETED");
    assertThat(
            jdbc.queryForList(
                "SELECT type FROM outbox_events WHERE aggregate_type='ORDER' AND aggregate_id=?"
                    + " ORDER BY id",
                String.class,
                Long.toString(id)))
        .containsExactly(
            "ORDER_CREATED",
            "ORDER_ACCEPTED",
            "ORDER_CONTRACT_CREATED",
            "ORDER_CONTRACT_SIGNED",
            "ORDER_FUNDS_HELD",
            "ORDER_DELIVERY_DUE_NOTIFIED",
            "ORDER_DELIVERED",
            "ORDER_FUNDS_RELEASED");
  }

  // ---------- B1 ----------

  @Test
  void cancellationBeforePaymentMovesNoMoneyAtEveryStage() {
    long beforeAccept = requested();
    assertThat(result(() -> cancellations.cancel(seller, Long.toString(beforeAccept), "취소")))
        .isEqualTo("ORDER_STATE_CONFLICT");
    assertThat(cancellations.cancel(buyer, Long.toString(beforeAccept), "수락 전 취소").status())
        .isEqualTo("CANCELLED");

    long awaitingContract = accepted();
    assertThat(cancellations.cancel(buyer, Long.toString(awaitingContract), "계약 전 취소").status())
        .isEqualTo("CANCELLED");

    long buyerAfterSigning = signed();
    assertThat(
            cancellations.cancel(buyer, Long.toString(buyerAfterSigning), "결제 전 취소").status())
        .isEqualTo("CANCELLED");
    long sellerAfterSigning = signed();
    assertThat(
            cancellations
                .cancel(seller, Long.toString(sellerAfterSigning), "결제 전 취소")
                .status())
        .isEqualTo("CANCELLED");

    for (long id : ids) {
      assertThat(orderStatus(id)).isEqualTo("CANCELLED");
      assertThat(moneyRows(id)).isZero();
      assertThat(result(() -> payments.pay(buyer, Long.toString(id), true))).isNotEqualTo("OK");
    }
    assertThat(balance(buyer)).isEqualTo(GRANT);
    assertThat(balance(seller)).isEqualTo(GRANT);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM wallet_entries WHERE member_id IN (?,?) AND type<>'INITIAL_GRANT'",
                Long.class,
                buyer,
                seller))
        .isZero();
  }

  // ---------- C5 ----------

  @Test
  void expiryRacingBuyerResponsesAutoConfirmsOnceAndRejectsLateResponses() throws Exception {
    long id = timerActive();
    String order = Long.toString(id);
    shiftTimersPastExpiry(id);
    var results =
        parallel(
            () -> Integer.toString(timers.expireDue(10)),
            () ->
                result(
                    () ->
                        revisions.request(
                            buyer,
                            order,
                            new CreateRevisionRequest(Long.toString(deliveryId(id)), "수정"))),
            () ->
                result(
                    () ->
                        requests.create(
                            buyer,
                            order,
                            new CreateCancellationRequest("취소", UUID.randomUUID().toString()))),
            () -> result(() -> confirmations.confirm(buyer, order)));

    // 만료 시각이 지났으므로 어느 쪽이 잠금을 먼저 잡아도 자동 확정만 결과가 된다.
    assertThat(results.get(0)).isEqualTo("1");
    assertThat(results.get(1)).isNotEqualTo("OK");
    assertThat(results.get(2)).isNotEqualTo("OK");
    // 구매 확정은 만료 전 잠금이면 AUTO_CONFIRM_DUE, 자동 확정 뒤면 기존 완료 결과 확인(OK)이다.
    assertThat(results.get(3)).isIn("AUTO_CONFIRM_DUE", "OK");
    assertThat(orderStatus(id)).isEqualTo("COMPLETED");
    assertThat(jdbc.queryForObject("SELECT reason FROM order_closures WHERE order_id=?", String.class, id))
        .isEqualTo("AUTO_CONFIRMED");
    assertThat(timerStatus(id)).isEqualTo("EXPIRED");
    assertThat(count("revision_requests", id)).isZero();
    assertThat(count("cancellation_requests", id)).isZero();
    assertThat(count("settlements", id)).isOne();
    assertThat(balance(seller)).isEqualTo(GRANT + PAYOUT);
  }

  @Test
  void responseCommittedBeforeExpiryLeavesNothingForLaterExpiryRun() throws Exception {
    long id = timerActive();
    String order = Long.toString(id);
    var results =
        parallel(
            () -> Integer.toString(timers.expireDue(10)),
            () ->
                result(
                    () ->
                        revisions.request(
                            buyer,
                            order,
                            new CreateRevisionRequest(Long.toString(deliveryId(id)), "수정"))));
    assertThat(results).containsExactly("0", "OK");
    assertThat(timerStatus(id)).isEqualTo("CANCELLED");

    // 끝난 타이머의 만료 시각이 지나도 다시 집어 지급하지 않는다.
    shiftTimersPastExpiry(id);
    assertThat(timers.expireDue(10)).isZero();
    assertThat(orderStatus(id)).isEqualTo("REVISION_IN_PROGRESS");
    assertThat(count("order_closures", id)).isZero();
    assertThat(balance(seller)).isEqualTo(GRANT);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM order_funds WHERE order_id=?", String.class, id))
        .isEqualTo("HELD");
  }

  // ---------- C6 ----------

  @Test
  void adminStopRacingDueExpiryLosesToAutoConfirmation() throws Exception {
    long id = timerActive();
    shiftTimersPastExpiry(id);
    var results =
        parallel(
            () -> Integer.toString(timers.expireDue(10)),
            () -> result(() -> timers.adminStop(admin, id, "운영 문의 확인")));
    assertThat(results.get(0)).isEqualTo("1");
    assertThat(results.get(1)).isIn("AUTO_CONFIRM_DUE", "TIMER_NOT_ACTIVE");
    assertThat(orderStatus(id)).isEqualTo("COMPLETED");
    assertThat(timerStatus(id)).isEqualTo("EXPIRED");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_response_timers WHERE order_id=? AND end_reason='ADMIN_STOP'",
                Long.class,
                id))
        .isZero();
    assertThat(balance(seller)).isEqualTo(GRANT + PAYOUT);
  }

  @Test
  void adminStopBeforeExpiryLeavesNothingForLaterExpiryRun() throws Exception {
    long id = timerActive();
    var results =
        parallel(
            () -> Integer.toString(timers.expireDue(10)),
            () -> result(() -> timers.adminStop(admin, id, "운영 문의 확인")));
    assertThat(results).containsExactly("0", "OK");
    assertThat(timerStatus(id)).isEqualTo("CANCELLED");

    shiftTimersPastExpiry(id);
    assertThat(timers.expireDue(10)).isZero();
    assertThat(orderStatus(id)).isEqualTo("AWAITING_CONFIRMATION");
    assertThat(count("order_closures", id)).isZero();
    assertThat(balance(seller)).isEqualTo(GRANT);
  }
}
