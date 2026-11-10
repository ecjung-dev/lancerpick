package com.example.lancerpick.order;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.contract.service.ContractDocumentService;
import com.example.lancerpick.contract.service.OrderContractService;
import com.example.lancerpick.member.config.SecurityLockRetrySchedulingConfiguration;
import com.example.lancerpick.member.dto.SecurityLockApplied;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.entity.SanctionKind;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.member.service.MemberSanctionCommandService;
import com.example.lancerpick.member.service.SecurityLockDelivery;
import com.example.lancerpick.notification.service.OrderEventPublisher;
import com.example.lancerpick.notification.service.OutboxDispatcher;
import com.example.lancerpick.order.dto.CreateCancellationRequest;
import com.example.lancerpick.order.dto.CreateDeliveryRequest;
import com.example.lancerpick.order.dto.CreateRevisionRequest;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderSnapshot;
import com.example.lancerpick.order.entity.OrderTradeTerms;
import com.example.lancerpick.order.repository.OrderRepository;
import com.example.lancerpick.order.repository.OrderSnapshotRepository;
import com.example.lancerpick.order.repository.OrderTradeTermsRepository;
import com.example.lancerpick.order.service.DeadlineNotices;
import com.example.lancerpick.order.service.FullFundingService;
import com.example.lancerpick.order.service.OrderCancellationRequestService;
import com.example.lancerpick.order.service.OrderConfirmationService;
import com.example.lancerpick.order.service.OrderDeliveryService;
import com.example.lancerpick.order.service.OrderPaymentService;
import com.example.lancerpick.order.service.OrderRevisionService;
import com.example.lancerpick.order.service.ReviewTimerService;
import com.example.lancerpick.order.service.SecurityLockTimerStops;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.support.V3FundsInvariant;
import com.example.lancerpick.wallet.entity.Wallet;
import com.example.lancerpick.wallet.entity.WalletEntry;
import com.example.lancerpick.wallet.repository.WalletEntryRepository;
import com.example.lancerpick.wallet.repository.WalletRepository;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * E7 검수 무응답 자동 구매 확정과 E8 기한 통지·E9 V3 알림(보관·지급·반환·자동 확정 사전 안내). 실제 커밋·잠금으로 검사하며, 48시간·7일 경과는 DB의
 * 납품·타이머 시각을 같은 폭으로 과거로 옮겨 재현한다(DB CHECK의 시각 관계는 유지된다).
 */
@IntegrationTest
@AutoConfigureMockMvc
class ReviewTimerIntegrationTests {
  static final long AMOUNT = 600001;
  static final long PAYOUT = 540001;
  static final Path directory = temp();

  static Path temp() {
    try {
      return Files.createTempDirectory("lancerpick-review-timer-");
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry p) {
    p.add("contracts.storage-root", directory::toString);
  }

  @Autowired MemberRepository members;
  @Autowired WalletRepository wallets;
  @Autowired WalletEntryRepository entries;
  @Autowired OrderRepository orders;
  @Autowired OrderSnapshotRepository snapshots;
  @Autowired OrderTradeTermsRepository terms;
  @Autowired OrderContractService contracts;
  @Autowired ContractDocumentService documents;
  @Autowired OrderPaymentService payments;
  @Autowired OrderConfirmationService confirmations;
  @Autowired OrderDeliveryService deliveries;
  @Autowired OrderRevisionService revisions;
  @Autowired OrderCancellationRequestService requests;
  @Autowired FullFundingService funding;
  @Autowired ReviewTimerService timers;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @Autowired MockMvc mvc;
  @Autowired OutboxDispatcher dispatcher;
  @Autowired DeadlineNotices deadlineNotices;
  @Autowired MemberSanctionCommandService sanctionCommands;
  @Autowired SecurityLockTimerStops timerStops;
  @Autowired SecurityLockDelivery lockDelivery;
  @MockitoSpyBean OrderEventPublisher events;
  long buyer, seller, other, admin, product, category, id;
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
          other = member(at);
          admin = member(at);
          jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", admin);
          String name = "review-timer-" + UUID.randomUUID();
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
    id = create();
  }

  long member(LocalDateTime at) {
    long m =
        members
            .saveAndFlush(Member.create(UUID.randomUUID() + "@timer.test", "fixture", "표시 이름", at))
            .getId();
    wallets.saveAndFlush(Wallet.withInitialGrant(m, at));
    entries.saveAndFlush(WalletEntry.initialGrant(m, at));
    return m;
  }

  long create() {
    long result =
        new TransactionTemplate(manager)
            .execute(ignored -> {
              var at = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
              var o =
                  Order.create(
                      buyer,
                      seller,
                      product,
                      UUID.randomUUID(),
                      AMOUNT,
                      1,
                      at,
                      TradePolicyVersion.CONTRACT_V3);
              o.accept(at);
              orders.saveAndFlush(o);
              ids.add(o.getId());
              terms.saveAndFlush(OrderTradeTerms.fullFunding(o.getId()));
              snapshots.saveAndFlush(
                  OrderSnapshot.create(o.getId(), 0L, "상품", "설명", "범위", "준비", "전액 보관", "요구사항"));
              return o.getId();
            });
    var draft = contracts.prepare(seller, result, 0, "");
    var ready = documents.generate(seller, result, draft.version());
    contracts.sign(buyer, result, ready.version(), ready.contentHash(), ready.fileHash());
    contracts.sign(seller, result, ready.version(), ready.contentHash(), ready.fileHash());
    return result;
  }

  @AfterEach
  void cleanup() throws Exception {
    // G1 금액 불변식: 지우기 전에 확인하고, 정리는 항상 끝낸 뒤 판정한다.
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
    jdbc.update(
        "DELETE FROM member_sanctions WHERE member_id IN (?,?,?,?)", buyer, seller, other, admin);
    jdbc.update("DELETE FROM products WHERE id=?", product);
    jdbc.update("DELETE FROM categories WHERE id=?", category);
    jdbc.update(
        "DELETE FROM wallet_entries WHERE member_id IN (?,?,?,?)", buyer, seller, other, admin);
    jdbc.update("DELETE FROM wallets WHERE member_id IN (?,?,?,?)", buyer, seller, other, admin);
    jdbc.update("DELETE FROM members WHERE id IN (?,?,?,?)", buyer, seller, other, admin);
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

  String str() {
    return Long.toString(id);
  }

  long balance(long m) {
    return wallets.findById(m).orElseThrow().getBalance();
  }

  long count(String table) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE order_id=?", Long.class, id);
  }

  String orderStatus() {
    return jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id);
  }

  void paidAndDelivered() {
    payments.pay(buyer, str(), true);
    deliver(1);
  }

  void deliver(int sequence) {
    deliveries.submit(
        seller, str(), new CreateDeliveryRequest("납품", "https://example.invalid/result", sequence));
  }

  /** 납품·취소 처리 시각을 과거로 옮겨 기산점+48시간을 지난 상태를 만든다. */
  void reviewWindowPassed() {
    jdbc.update(
        "UPDATE deliveries SET submitted_at=submitted_at - INTERVAL 49 HOUR WHERE order_id=?", id);
    jdbc.update(
        "UPDATE cancellation_requests SET requested_at=requested_at - INTERVAL 49 HOUR,"
            + " resolved_at=resolved_at - INTERVAL 49 HOUR WHERE order_id=? AND resolved_at IS NOT"
            + " NULL",
        id);
  }

  /** 활성 타이머의 모든 시각을 같은 폭으로 옮겨 7일 만료를 재현한다. */
  void timerExpired() {
    jdbc.update(
        "UPDATE order_response_timers SET basis_at=basis_at - INTERVAL 169 HOUR,"
            + " eligible_at=eligible_at - INTERVAL 169 HOUR, started_at=started_at - INTERVAL 169"
            + " HOUR, expires_at=expires_at - INTERVAL 169 HOUR WHERE order_id=? AND"
            + " status='ACTIVE'",
        id);
  }

  void startTimer() {
    reviewWindowPassed();
    assertThat(timers.start(seller, id).status()).isEqualTo("ACTIVE");
  }

  long deliveryId(int sequence) {
    return jdbc.queryForObject(
        "SELECT id FROM deliveries WHERE order_id=? AND sequence_no=?", Long.class, id, sequence);
  }

  String timerColumn(String column) {
    return jdbc.queryForObject(
        "SELECT "
            + column
            + " FROM order_response_timers WHERE order_id=? ORDER BY id DESC LIMIT 1",
        String.class,
        id);
  }

  long outbox(String type) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM outbox_events WHERE aggregate_type='ORDER' AND aggregate_id=? AND"
            + " type=?",
        Long.class,
        id,
        type);
  }

  String result(Runnable action) {
    try {
      action.run();
      return "OK";
    } catch (BusinessException e) {
      return e.code();
    }
  }

  @SafeVarargs
  final List<String> parallel(Callable<String>... jobs) throws Exception {
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

  static void error(Runnable task, String code) {
    assertThatThrownBy(task::run)
        .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  // ---------- tests ----------

  @Test
  void sellerStartsAfter48HoursAndExpiryAutoConfirmsOnceAsSystem() {
    paidAndDelivered();
    assertThat(timers.status(seller, id).status()).isEqualTo("WAITING");
    error(() -> timers.start(seller, id), "TIMER_NOT_ELIGIBLE");
    error(() -> timers.start(buyer, id), "FORBIDDEN");
    reviewWindowPassed();
    assertThat(timers.status(seller, id).canStart()).isTrue();
    assertThat(timers.status(buyer, id).canStart()).isFalse();
    assertThat(timers.status(buyer, id).status()).isEqualTo("STARTABLE");

    var started = timers.start(seller, id);
    assertThat(started.status()).isEqualTo("ACTIVE");
    assertThat(started.expiresAt()).isEqualTo(started.startedAt().plusHours(168));
    assertThat(timers.start(seller, id).startedAt()).isEqualTo(started.startedAt());
    assertThat(count("order_response_timers")).isEqualTo(1);
    assertThat(outbox("ORDER_TIMER_STARTED")).isEqualTo(1);
    assertThat(timers.expireDue(10)).isZero();

    timerExpired();
    // 만료 시각 이후의 구매자 응답은 타이머를 취소하지 않는다(만료 우선).
    error(() -> confirmations.confirm(buyer, str()), "AUTO_CONFIRM_DUE");
    error(
        () ->
            revisions.request(
                buyer, str(), new CreateRevisionRequest(Long.toString(deliveryId(1)), "수정")),
        "AUTO_CONFIRM_DUE");
    error(
        () ->
            requests.create(
                buyer, str(), new CreateCancellationRequest("취소", UUID.randomUUID().toString())),
        "AUTO_CONFIRM_DUE");

    assertThat(timers.expireDue(10)).isEqualTo(1);
    assertThat(timers.expireDue(10)).isZero();
    assertThat(orderStatus()).isEqualTo("COMPLETED");
    assertThat(balance(seller)).isEqualTo(1000000 + PAYOUT);
    assertThat(balance(buyer)).isEqualTo(1000000 - AMOUNT);
    assertThat(count("settlements")).isEqualTo(1);
    var closure =
        jdbc.queryForMap(
            "SELECT reason, actor_type, actor_id, source_timer_id, fee_amount, seller_net_amount"
                + " FROM order_closures WHERE order_id=?",
            id);
    assertThat(closure.get("reason")).isEqualTo("AUTO_CONFIRMED");
    assertThat(closure.get("actor_type")).isEqualTo("SYSTEM");
    assertThat(closure.get("actor_id")).isNull();
    assertThat(closure.get("source_timer_id")).isNotNull();
    assertThat(((Number) closure.get("seller_net_amount")).longValue()).isEqualTo(PAYOUT);
    assertThat(timerColumn("status")).isEqualTo("EXPIRED");
    assertThat(timerColumn("end_reason")).isEqualTo("TIMEOUT");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_histories WHERE order_id=? AND action='COMPLETED' AND"
                    + " actor_type='SYSTEM' AND actor_id IS NULL",
                Long.class,
                id))
        .isEqualTo(1);
    assertThat(outbox("ORDER_AUTO_CONFIRMED")).isEqualTo(1);
    assertThat(outbox("ORDER_COMPLETED")).isZero();
    assertThat(timers.status(buyer, id).status()).isEqualTo("AUTO_CONFIRMED");
    assertThat(funding.summary(seller, id).status()).isEqualTo("RELEASED");
    // 자동 확정 후 구매자 확정·결제 재전송은 기존 완료 기록을 확인하고 추가 지급하지 않는다.
    confirmations.confirm(buyer, str());
    payments.pay(buyer, str(), true);
    assertThat(balance(seller)).isEqualTo(1000000 + PAYOUT);
  }

  @Test
  void revisionRequestCancelsTimerAndRedeliveryBecomesNewBasis() {
    paidAndDelivered();
    startTimer();
    revisions.request(buyer, str(), new CreateRevisionRequest(Long.toString(deliveryId(1)), "수정"));
    assertThat(timerColumn("status")).isEqualTo("CANCELLED");
    assertThat(timerColumn("end_reason")).isEqualTo("BUYER_RESPONSE");
    assertThat(timerColumn("ended_by")).isEqualTo(Long.toString(buyer));
    assertThat(outbox("ORDER_TIMER_CANCELLED")).isZero();
    assertThat(timers.status(seller, id).status()).isEqualTo("NOT_IN_REVIEW");

    deliver(2);
    assertThat(timers.status(seller, id).status()).isEqualTo("WAITING");
    error(() -> timers.start(seller, id), "TIMER_NOT_ELIGIBLE");
    reviewWindowPassed();
    timers.start(seller, id);
    assertThat(timerColumn("delivery_id")).isEqualTo(Long.toString(deliveryId(2)));
    assertThat(timerColumn("cycle_no")).isEqualTo("1");
    timerExpired();
    assertThat(timers.expireDue(10)).isEqualTo(1);
    assertThat(balance(seller)).isEqualTo(1000000 + PAYOUT);
  }

  @Test
  void cancellationRequestCancelsTimerAndRejectionRestartsReviewWindow() {
    paidAndDelivered();
    startTimer();
    requests.create(
        buyer, str(), new CreateCancellationRequest("취소 요청", UUID.randomUUID().toString()));
    assertThat(timerColumn("status")).isEqualTo("CANCELLED");
    assertThat(timers.status(seller, id).status()).isEqualTo("CANCELLATION_PENDING");
    error(() -> timers.start(seller, id), "CANCELLATION_PENDING");

    long request =
        jdbc.queryForObject(
            "SELECT id FROM cancellation_requests WHERE order_id=?", Long.class, id);
    requests.resolve(
        seller,
        str(),
        Long.toString(request),
        OrderCancellationRequestService.Resolution.REJECTED,
        "작업 계속");
    // 납품 후 48시간은 이미 지났지만 거절 시각이 새 기산점이라 다시 48시간을 기다린다.
    var waiting = timers.status(seller, id);
    assertThat(waiting.status()).isEqualTo("WAITING");
    error(() -> timers.start(seller, id), "TIMER_NOT_ELIGIBLE");

    reviewWindowPassed();
    timers.start(seller, id);
    assertThat(timerColumn("cycle_no")).isEqualTo("2");
    assertThat(timerColumn("delivery_id")).isEqualTo(Long.toString(deliveryId(1)));
  }

  @Test
  void manualConfirmationClosesActiveTimer() {
    paidAndDelivered();
    startTimer();
    confirmations.confirm(buyer, str());
    assertThat(orderStatus()).isEqualTo("COMPLETED");
    assertThat(timerColumn("status")).isEqualTo("CANCELLED");
    assertThat(timerColumn("end_reason")).isEqualTo("ORDER_CLOSED");
    assertThat(
            jdbc.queryForObject(
                "SELECT reason FROM order_closures WHERE order_id=?", String.class, id))
        .isEqualTo("COMPLETED");
    assertThat(timers.expireDue(10)).isZero();
    assertThat(balance(seller)).isEqualTo(1000000 + PAYOUT);
  }

  @Test
  void adminStopIsAdminOnlyAndBlocksRestartForSameBasis() {
    paidAndDelivered();
    startTimer();
    error(() -> timers.adminStop(seller, id, "해제"), "FORBIDDEN");
    error(() -> timers.adminStop(admin, id, "   "), "VALIDATION_FAILED");
    var stopped = timers.adminStop(admin, id, "운영 문의 확인");
    assertThat(stopped.status()).isEqualTo("ADMIN_STOPPED");
    assertThat(timerColumn("end_reason")).isEqualTo("ADMIN_STOP");
    assertThat(timerColumn("admin_stop_reason")).isEqualTo("운영 문의 확인");
    assertThat(outbox("ORDER_TIMER_ADMIN_STOPPED")).isEqualTo(1);
    error(() -> timers.start(seller, id), "TIMER_ADMIN_STOPPED");
    error(() -> timers.adminStop(admin, id, "다시"), "TIMER_NOT_ACTIVE");
    assertThat(timers.expireDue(10)).isZero();
    assertThat(orderStatus()).isEqualTo("AWAITING_CONFIRMATION");
  }

  @Test
  void adminCannotStopDueTimerBecauseExpiryWins() {
    paidAndDelivered();
    startTimer();
    timerExpired();
    error(() -> timers.adminStop(admin, id, "해제"), "AUTO_CONFIRM_DUE");
    assertThat(timers.expireDue(10)).isEqualTo(1);
  }

  @Test
  void concurrentExpiryRunsPaySellerOnlyOnce() throws Exception {
    paidAndDelivered();
    startTimer();
    timerExpired();
    var results =
        parallel(
            () -> Integer.toString(timers.expireDue(10)),
            () -> Integer.toString(timers.expireDue(10)));
    assertThat(results.stream().mapToInt(Integer::parseInt).sum()).isEqualTo(1);
    assertThat(balance(seller)).isEqualTo(1000000 + PAYOUT);
    assertThat(count("settlements")).isEqualTo(1);
    assertThat(count("order_closures")).isEqualTo(1);
  }

  @Test
  void autoConfirmFailureRollsBackPayoutAndKeepsTimerActive() {
    paidAndDelivered();
    startTimer();
    timerExpired();
    var publisher =
        org.springframework.test.util.AopTestUtils.<OrderEventPublisher>getUltimateTargetObject(
            events);
    doThrow(new IllegalStateException("outbox unavailable")).when(publisher).publish(any(), any());
    assertThat(timers.expireDue(10)).isZero();
    assertThat(balance(seller)).isEqualTo(1000000);
    assertThat(count("order_closures")).isZero();
    assertThat(timerColumn("status")).isEqualTo("ACTIVE");
    assertThat(orderStatus()).isEqualTo("AWAITING_CONFIRMATION");
    reset(publisher);
    assertThat(timers.expireDue(10)).isEqualTo(1);
    assertThat(balance(seller)).isEqualTo(1000000 + PAYOUT);
  }

  @Test
  void persistentlyFailingTimerDoesNotBlockLaterDueTimers() {
    paidAndDelivered();
    startTimer();
    timerExpired();
    long first = id;
    long originalBuyer = buyer;
    // 두 번째 주문은 다른 구매자로 결제해 잔액 부족 없이 만든다. 시작이 늦어 만료도 더 늦다.
    buyer = other;
    id = create();
    long second = id;
    paidAndDelivered();
    startTimer();
    timerExpired();
    buyer = originalBuyer;
    id = first;

    var publisher =
        org.springframework.test.util.AopTestUtils.<OrderEventPublisher>getUltimateTargetObject(
            events);
    doThrow(new IllegalStateException("outbox unavailable"))
        .when(publisher)
        .publish(argThat(order -> order != null && order.getId() == first), any());
    // 배치 크기 1에서도 먼저 만료된 실패 건 뒤의 정상 타이머까지 같은 실행에서 처리한다.
    assertThat(timers.expireDue(1)).isEqualTo(1);
    assertThat(timers.expireDue(1)).isZero();
    assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, second))
        .isEqualTo("COMPLETED");
    assertThat(orderStatus()).isEqualTo("AWAITING_CONFIRMATION");
    assertThat(timerColumn("status")).isEqualTo("ACTIVE");
    assertThat(balance(seller)).isEqualTo(1000000 + PAYOUT);
  }

  @Test
  void httpEndpointsCheckParticipantRoleAndCsrf() throws Exception {
    paidAndDelivered();
    reviewWindowPassed();
    mvc.perform(get("/api/orders/" + id + "/review-timer").with(authentication(auth(buyer))))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.status").value("STARTABLE"))
        .andExpect(jsonPath("$.canStart").value(false));
    mvc.perform(get("/api/orders/" + id + "/review-timer").with(authentication(auth(other))))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/orders/" + id + "/review-timer")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/orders/" + id + "/review-timer").with(authentication(auth(seller))))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/orders/" + id + "/review-timer")
                .with(authentication(auth(buyer)))
                .with(csrf()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mvc.perform(
            post("/api/orders/" + id + "/review-timer")
                .with(authentication(auth(seller)))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.expiresAt").isNotEmpty());
  }

  // ---------- E8 기한 통지 · E9 알림 ----------

  Map<String, Object> deadlineHistory(String sourceKey) {
    return jdbc.queryForMap(
        "SELECT actor_type, actor_id, from_status, to_status,"
            + " JSON_UNQUOTE(JSON_EXTRACT(details,'$.kind')) kind,"
            + " JSON_UNQUOTE(JSON_EXTRACT(details,'$.basisAt')) basis_at,"
            + " JSON_UNQUOTE(JSON_EXTRACT(details,'$.dueAt')) due_at,"
            + " JSON_EXTRACT(details,'$.deliverySequence') delivery_sequence,"
            + " JSON_EXTRACT(details,'$.contractVersion') contract_version"
            + " FROM order_histories WHERE order_id=? AND action='DEADLINE_NOTIFIED' AND"
            + " source_event_key=?",
        id,
        sourceKey);
  }

  long deadlineHistories() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM order_histories WHERE order_id=? AND action='DEADLINE_NOTIFIED'",
        Long.class,
        id);
  }

  static String utc(LocalDateTime value) {
    return value.atOffset(ZoneOffset.UTC).toString();
  }

  LocalDateTime time(String sql, Object... args) {
    return jdbc.queryForObject(sql, LocalDateTime.class, args);
  }

  long paymentId() {
    return jdbc.queryForObject("SELECT id FROM payments WHERE order_id=?", Long.class, id);
  }

  /** 이 주문의 대기 이벤트만 알림으로 바꾸고 수신자의 알림 유형을 생성 순서로 돌려준다. */
  List<String> dispatchedTypes(long recipient) {
    for (long event :
        jdbc.queryForList(
            "SELECT id FROM outbox_events WHERE aggregate_type='ORDER' AND aggregate_id=? AND"
                + " status='PENDING' ORDER BY id",
            Long.class,
            id)) dispatcher.dispatch(event);
    return jdbc.queryForList(
        "SELECT type FROM notifications WHERE order_id=? AND recipient_id=? ORDER BY id",
        String.class,
        id,
        recipient);
  }

  @Test
  void paymentNotifiesDeliveryDeadlineOnceToBothPartiesWithV3HeldWording() {
    payments.pay(buyer, str(), true);
    long payment = paymentId();
    var row = deadlineHistory("DEADLINE:PAYMENT:" + payment);
    assertThat(row.get("actor_type")).isEqualTo("SYSTEM");
    assertThat(row.get("actor_id")).isNull();
    assertThat(row.get("from_status")).isEqualTo("IN_PROGRESS");
    assertThat(row.get("to_status")).isEqualTo("IN_PROGRESS");
    assertThat(row.get("kind")).isEqualTo("DELIVERY_DUE");
    assertThat(row.get("basis_at"))
        .isEqualTo(utc(time("SELECT paid_at FROM orders WHERE id=?", id)));
    assertThat(row.get("due_at")).isEqualTo(utc(time("SELECT due_at FROM orders WHERE id=?", id)));
    assertThat(String.valueOf(row.get("delivery_sequence"))).isEqualTo("1");
    assertThat(String.valueOf(row.get("contract_version"))).isEqualTo("1");

    // 결제 재전송은 기존 기록을 확인할 뿐 통지를 다시 만들지 않는다.
    payments.pay(buyer, str(), true);
    assertThat(deadlineHistories()).isEqualTo(1);
    // 같은 결제로 통지를 다시 기록하려 해도 원천 키 UNIQUE가 막는다.
    assertThatThrownBy(() ->
            new TransactionTemplate(manager)
                .executeWithoutResult(ignored ->
                    deadlineNotices.deliveryDue(orders.findById(id).orElseThrow(), payment)))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(deadlineHistories()).isEqualTo(1);

    assertThat(outbox("ORDER_DELIVERY_DUE_NOTIFIED")).isEqualTo(1);
    assertThat(outbox("ORDER_FUNDS_HELD")).isEqualTo(1);
    assertThat(outbox("ORDER_PAID")).isZero();
    // 결제 이전의 계약 알림을 제외하고, 결제로 생긴 알림만 본다.
    assertThat(dispatchedTypes(seller))
        .containsSubsequence("ORDER_FUNDS_HELD", "ORDER_DELIVERY_DUE_NOTIFIED")
        .doesNotContain("ORDER_PAID");
    assertThat(dispatchedTypes(buyer))
        .contains("ORDER_DELIVERY_DUE_NOTIFIED")
        .doesNotContain("ORDER_FUNDS_HELD");
  }

  @Test
  void revisionNotifiesRedeliveryDeadlineFromRequestTime() {
    paidAndDelivered();
    revisions.request(buyer, str(), new CreateRevisionRequest(Long.toString(deliveryId(1)), "수정"));
    long revision =
        jdbc.queryForObject("SELECT id FROM revision_requests WHERE order_id=?", Long.class, id);
    var row = deadlineHistory("DEADLINE:REVISION:" + revision);
    assertThat(row.get("to_status")).isEqualTo("REVISION_IN_PROGRESS");
    assertThat(row.get("kind")).isEqualTo("REDELIVERY_DUE");
    assertThat(row.get("basis_at"))
        .isEqualTo(utc(time("SELECT requested_at FROM revision_requests WHERE id=?", revision)));
    assertThat(row.get("due_at"))
        .isEqualTo(utc(time("SELECT due_at FROM revision_requests WHERE id=?", revision)));
    assertThat(String.valueOf(row.get("delivery_sequence"))).isEqualTo("2");
    assertThat(deadlineHistories()).isEqualTo(2);
    assertThat(outbox("ORDER_REDELIVERY_DUE_NOTIFIED")).isEqualTo(1);
    assertThat(dispatchedTypes(seller)).contains("ORDER_REDELIVERY_DUE_NOTIFIED");
    assertThat(dispatchedTypes(buyer)).contains("ORDER_REDELIVERY_DUE_NOTIFIED");
  }

  @Test
  void manualConfirmationAndApprovedRefundUseV3FundsWording() {
    paidAndDelivered();
    confirmations.confirm(buyer, str());
    assertThat(outbox("ORDER_FUNDS_RELEASED")).isEqualTo(1);
    assertThat(outbox("ORDER_COMPLETED")).isZero();
    assertThat(dispatchedTypes(seller)).contains("ORDER_FUNDS_RELEASED");

    long originalBuyer = buyer;
    buyer = other;
    id = create();
    payments.pay(buyer, str(), true);
    requests.create(
        buyer, str(), new CreateCancellationRequest("취소 요청", UUID.randomUUID().toString()));
    funding.refund(seller, id, "취소 승인");
    assertThat(outbox("ORDER_FUNDS_REFUNDED")).isEqualTo(1);
    assertThat(outbox("ORDER_CANCELLED")).isZero();
    assertThat(dispatchedTypes(buyer)).contains("ORDER_FUNDS_REFUNDED");
    buyer = originalBuyer;
  }

  /** 활성 타이머의 시각을 같은 폭으로 옮겨 만료까지 23시간 남은 상태를 만든다. */
  void timerInNoticeWindow() {
    jdbc.update(
        "UPDATE order_response_timers SET basis_at=basis_at - INTERVAL 145 HOUR,"
            + " eligible_at=eligible_at - INTERVAL 145 HOUR, started_at=started_at - INTERVAL 145"
            + " HOUR, expires_at=expires_at - INTERVAL 145 HOUR WHERE order_id=? AND"
            + " status='ACTIVE'",
        id);
  }

  @Test
  void autoConfirmNoticeIsSentOnceToBuyerWithin24HoursOfExpiry() throws Exception {
    paidAndDelivered();
    startTimer();
    timers.noticeDue(10);
    assertThat(outbox(OrderEventPublisher.AUTO_CONFIRM_SOON)).isZero();

    timerInNoticeWindow();
    parallel(
        () -> Integer.toString(timers.noticeDue(10)), () -> Integer.toString(timers.noticeDue(10)));
    timers.noticeDue(10);
    assertThat(outbox(OrderEventPublisher.AUTO_CONFIRM_SOON)).isEqualTo(1);
    long timer =
        jdbc.queryForObject(
            "SELECT id FROM order_response_timers WHERE order_id=? AND status='ACTIVE'",
            Long.class,
            id);
    assertThat(
            jdbc.queryForObject(
                "SELECT event_id FROM outbox_events WHERE aggregate_id=? AND type=?",
                String.class,
                id,
                OrderEventPublisher.AUTO_CONFIRM_SOON))
        .isEqualTo(OrderEventPublisher.autoConfirmNoticeEventId(timer));
    assertThat(dispatchedTypes(buyer)).contains(OrderEventPublisher.AUTO_CONFIRM_SOON);
    assertThat(dispatchedTypes(seller)).doesNotContain(OrderEventPublisher.AUTO_CONFIRM_SOON);
    // 사전 안내는 타이머·주문 상태를 바꾸지 않는다.
    assertThat(timerColumn("status")).isEqualTo("ACTIVE");
    assertThat(orderStatus()).isEqualTo("AWAITING_CONFIRMATION");

    timerExpired();
    assertThat(timers.expireDue(10)).isEqualTo(1);
    assertThat(dispatchedTypes(buyer)).contains("ORDER_AUTO_CONFIRMED");
    assertThat(dispatchedTypes(seller)).contains("ORDER_AUTO_CONFIRMED");
  }

  @Test
  void autoConfirmNoticeIsNotSentForEndedOrExpiredTimers() {
    paidAndDelivered();
    startTimer();
    timerInNoticeWindow();
    revisions.request(buyer, str(), new CreateRevisionRequest(Long.toString(deliveryId(1)), "수정"));
    timers.noticeDue(10);
    assertThat(outbox(OrderEventPublisher.AUTO_CONFIRM_SOON)).isZero();

    deliver(2);
    startTimer();
    timerExpired();
    // 이미 만료 시각이 지난 타이머에는 사전 안내 대신 자동 확정 결과만 보낸다.
    timers.noticeDue(10);
    assertThat(outbox(OrderEventPublisher.AUTO_CONFIRM_SOON)).isZero();
  }

  // ---------- U8 구매자 보안 잠금 ----------

  long lock(long member) {
    return sanctionCommands.apply(
        admin,
        member,
        SanctionKind.SECURITY_LOCK,
        "계정 도용 의심",
        LocalDateTime.now(ZoneOffset.UTC).plusDays(1),
        UUID.randomUUID());
  }

  void unlock(long member, long sanctionId) {
    sanctionCommands.revoke(admin, member, sanctionId, "본인 확인 완료");
  }

  long securityStops() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM order_histories WHERE order_id=? AND action='TIMER_ADMIN_STOPPED'"
            + " AND reason=?",
        Long.class,
        id,
        ReviewTimerService.SECURITY_LOCK_STOP_REASON);
  }

  long activeTimers() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM order_response_timers WHERE order_id=? AND status='ACTIVE'",
        Long.class,
        id);
  }

  @Test
  void buyerSecurityLockStopsActiveTimerOnceAndNeverResumesAfterUnlock() {
    paidAndDelivered();
    startTimer();
    long sanction = lock(buyer);

    assertThat(timerColumn("status")).isEqualTo("CANCELLED");
    assertThat(timerColumn("end_reason")).isEqualTo("ADMIN_STOP");
    assertThat(timerColumn("ended_by")).isEqualTo(Long.toString(admin));
    assertThat(timerColumn("admin_stop_reason"))
        .isEqualTo(ReviewTimerService.SECURITY_LOCK_STOP_REASON);
    assertThat(timerColumn("admin_stop_reason")).doesNotContain("계정 도용 의심");
    assertThat(securityStops()).isEqualTo(1);
    assertThat(outbox("ORDER_TIMER_ADMIN_STOPPED")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT timer_stop_delivered_at IS NOT NULL FROM member_sanctions WHERE id=?",
                Boolean.class,
                sanction))
        .isTrue();
    assertThat(timers.status(seller, id).status()).isEqualTo("ADMIN_STOPPED");
    error(() -> timers.start(seller, id), "BUYER_SECURITY_LOCKED");

    // 중복 전달은 아무것도 바꾸지 않는다.
    var appliedAt =
        jdbc.queryForObject(
            "SELECT starts_at FROM member_sanctions WHERE id=?", LocalDateTime.class, sanction);
    timerStops.stop(new SecurityLockApplied(sanction, admin, buyer, "계정 도용 의심", appliedAt));
    assertThat(securityStops()).isEqualTo(1);

    // 해제 후에도 같은 기산점으로 자동 재개하지 않고, 자동 확정·지급도 없다.
    unlock(buyer, sanction);
    error(() -> timers.start(seller, id), "TIMER_ADMIN_STOPPED");
    assertThat(timers.expireDue(10)).isZero();
    assertThat(orderStatus()).isEqualTo("AWAITING_CONFIRMATION");
    assertThat(balance(seller)).isEqualTo(1000000);
    assertThat(funding.summary(seller, id).status()).isEqualTo("HELD");
  }

  @Test
  void securityLockedBuyerCannotConfirmEvenIfRequestPassedAuthentication() {
    // 인증 필터를 이미 통과한 확정 요청을 서비스 직접 호출로 재현한다. 잠금 커밋 뒤에는 지급하지 않는다.
    paidAndDelivered();
    long sanction = lock(buyer);
    error(() -> confirmations.confirm(buyer, str()), "MEMBER_LOCKED");
    error(() -> funding.confirm(buyer, id), "MEMBER_LOCKED");
    assertThat(orderStatus()).isEqualTo("AWAITING_CONFIRMATION");
    assertThat(balance(seller)).isEqualTo(1000000);
    assertThat(funding.summary(seller, id).status()).isEqualTo("HELD");

    // 해제 후에는 정상 확정되고, 판매자 보안 잠금은 구매자 확정을 막지 않는다.
    unlock(buyer, sanction);
    lock(seller);
    confirmations.confirm(buyer, str());
    assertThat(orderStatus()).isEqualTo("COMPLETED");
  }

  @Test
  void securityLockedSellerCannotAdvanceTradeButCanApproveRefund() {
    // 인증 필터를 통과한 진행 중 요청을 서비스 직접 호출로 재현한다.
    payments.pay(buyer, str(), true);
    long sanction = lock(seller);
    error(() -> deliver(1), "MEMBER_LOCKED");
    assertThat(orderStatus()).isEqualTo("IN_PROGRESS");
    assertThat(count("deliveries")).isZero();

    unlock(seller, sanction);
    deliver(1);
    reviewWindowPassed();
    sanction = lock(seller);
    error(() -> timers.start(seller, id), "MEMBER_LOCKED");
    assertThat(count("order_response_timers")).isZero();

    // 취소 요청 거절은 막고, 구매자에게 전액 반환하는 승인은 허용한다.
    requests.create(
        buyer, str(), new CreateCancellationRequest("취소 요청", UUID.randomUUID().toString()));
    long request =
        jdbc.queryForObject(
            "SELECT id FROM cancellation_requests WHERE order_id=?", Long.class, id);
    error(
        () ->
            requests.resolve(
                seller,
                str(),
                Long.toString(request),
                OrderCancellationRequestService.Resolution.REJECTED,
                "작업 계속"),
        "MEMBER_LOCKED");
    requests.resolve(
        seller, str(), Long.toString(request), OrderCancellationRequestService.Resolution.APPROVED, null);
    assertThat(orderStatus()).isEqualTo("CANCELLED");
    assertThat(balance(seller)).isEqualTo(1000000);
  }

  @Test
  void retryAfterStopBeforeDeliveryMarkerDoesNotDuplicateTimerHistoryNotificationsOrPayout() {
    paidAndDelivered();
    startTimer();
    long sanction = lock(buyer);
    // Reproduce a crash after the order stop committed but before its delivery marker committed.
    jdbc.update("UPDATE member_sanctions SET timer_stop_delivered_at=NULL WHERE id=?", sanction);
    new SecurityLockRetrySchedulingConfiguration(lockDelivery, 100).retry();
    assertThat(
            jdbc.queryForObject(
                "SELECT timer_stop_delivered_at IS NOT NULL FROM member_sanctions WHERE id=?",
                Boolean.class,
                sanction))
        .isTrue();
    assertThat(timerColumn("status")).isEqualTo("CANCELLED");
    assertThat(timerColumn("end_reason")).isEqualTo("ADMIN_STOP");
    assertThat(securityStops()).isEqualTo(1);
    assertThat(outbox("ORDER_TIMER_ADMIN_STOPPED")).isEqualTo(1);
    assertThat(orderStatus()).isEqualTo("AWAITING_CONFIRMATION");
    assertThat(balance(seller)).isEqualTo(1000000);
    assertThat(funding.summary(seller, id).status()).isEqualTo("HELD");
  }

  @Test
  void lockedBuyerBlocksTimerStartUntilUnlocked() {
    paidAndDelivered();
    reviewWindowPassed();
    long sanction = lock(buyer);
    assertThat(timers.status(seller, id).status()).isEqualTo("STARTABLE");
    assertThat(timers.status(seller, id).canStart()).isFalse();
    error(() -> timers.start(seller, id), "BUYER_SECURITY_LOCKED");
    assertThat(count("order_response_timers")).isZero();

    // 잠금 중 타이머가 없었다면 해제 후 정상 시작할 수 있다.
    unlock(buyer, sanction);
    assertThat(timers.status(seller, id).canStart()).isTrue();
    assertThat(timers.start(seller, id).status()).isEqualTo("ACTIVE");
  }

  @Test
  void independentRetrySchedulersStopTheSamePendingTimerOnlyOnce() throws Exception {
    paidAndDelivered();
    startTimer();
    var appliedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    String key = UUID.randomUUID().toString();
    // Reproduce a committed lock whose after-commit callback never ran (process interruption).
    new org.springframework.transaction.support.TransactionTemplate(manager)
        .executeWithoutResult(ignored -> {
          jdbc.update(
              "INSERT INTO"
                  + " member_sanctions(member_id,kind,reason,applied_by,request_key,starts_at,ends_at)"
                  + " VALUES (?,'SECURITY_LOCK','복구 경합 확인',?,?,?,?)",
              buyer,
              admin,
              key,
              appliedAt,
              appliedAt.plusDays(1));
          jdbc.update(
              "UPDATE members SET auth_version=auth_version+1,updated_at=? WHERE id=?",
              appliedAt,
              buyer);
        });
    long sanction =
        jdbc.queryForObject(
            "SELECT id FROM member_sanctions WHERE member_id=? AND request_key=?",
            Long.class,
            buyer,
            key);
    var first = new SecurityLockRetrySchedulingConfiguration(lockDelivery, 100);
    var second = new SecurityLockRetrySchedulingConfiguration(lockDelivery, 100);
    assertThat(parallel(() -> result(first::retry), () -> result(second::retry)))
        .containsOnly("OK");
    assertThat(
            jdbc.queryForObject(
                "SELECT timer_stop_delivered_at IS NOT NULL FROM member_sanctions WHERE id=?",
                Boolean.class,
                sanction))
        .isTrue();
    assertThat(securityStops()).isEqualTo(1);
    assertThat(outbox("ORDER_TIMER_ADMIN_STOPPED")).isEqualTo(1);
    assertThat(timerColumn("status")).isEqualTo("CANCELLED");
    assertThat(timerColumn("end_reason")).isEqualTo("ADMIN_STOP");
    assertThat(orderStatus()).isEqualTo("AWAITING_CONFIRMATION");
    assertThat(funding.summary(seller, id).status()).isEqualTo("HELD");
    assertThat(balance(seller)).isEqualTo(1000000);
    assertThat(
            jdbc.queryForObject("SELECT auth_version FROM members WHERE id=?", Long.class, buyer))
        .isEqualTo(1);
  }

  @Test
  void lockOnAlreadyDueButUnprocessedTimerStopsInsteadOfAutoConfirm() {
    paidAndDelivered();
    startTimer();
    timerExpired();
    lock(buyer);
    assertThat(timerColumn("end_reason")).isEqualTo("ADMIN_STOP");
    assertThat(timers.expireDue(10)).isZero();
    assertThat(orderStatus()).isEqualTo("AWAITING_CONFIRMATION");
    assertThat(balance(seller)).isEqualTo(1000000);
  }

  @Test
  void expiryRechecksPastLockEvenWhenDeliveryNeverRanAndLockAlreadyEnded() {
    paidAndDelivered();
    startTimer();
    timerExpired();
    // 커밋 후 전달이 실패한 채 잠금 기간이 끝난 상태: 전달 기록 없음, 현재 제한 없음.
    var now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    jdbc.update(
        "INSERT INTO"
            + " member_sanctions(member_id,kind,reason,applied_by,request_key,starts_at,ends_at)"
            + " VALUES (?,'SECURITY_LOCK','계정 도용 의심',?,?,?,?)",
        buyer,
        admin,
        UUID.randomUUID().toString(),
        now.minusHours(2),
        now.minusHours(1));

    assertThat(timers.expireDue(10)).isZero();
    assertThat(timerColumn("status")).isEqualTo("CANCELLED");
    assertThat(timerColumn("end_reason")).isEqualTo("ADMIN_STOP");
    assertThat(timerColumn("ended_by")).isEqualTo(Long.toString(admin));
    assertThat(securityStops()).isEqualTo(1);
    assertThat(orderStatus()).isEqualTo("AWAITING_CONFIRMATION");
    assertThat(balance(seller)).isEqualTo(1000000);
    assertThat(count("settlements")).isZero();
  }

  @Test
  void noticeSkipsTimerCoveredByUndeliveredLock() {
    paidAndDelivered();
    startTimer();
    timerInNoticeWindow();
    var now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    jdbc.update(
        "INSERT INTO"
            + " member_sanctions(member_id,kind,reason,applied_by,request_key,starts_at,ends_at)"
            + " VALUES (?,'SECURITY_LOCK','계정 도용 의심',?,?,?,?)",
        buyer,
        admin,
        UUID.randomUUID().toString(),
        now,
        now.plusDays(1));
    timers.noticeDue(10);
    assertThat(outbox(OrderEventPublisher.AUTO_CONFIRM_SOON)).isZero();
  }

  @Test
  void lateDeliveryDoesNotStopTimerStartedAfterUnlockAndSellerLockIsIgnored() {
    paidAndDelivered();
    reviewWindowPassed();
    long sanction = lock(buyer);
    var appliedAt =
        jdbc.queryForObject(
            "SELECT starts_at FROM member_sanctions WHERE id=?", LocalDateTime.class, sanction);
    unlock(buyer, sanction);
    timers.start(seller, id);

    timerStops.stop(new SecurityLockApplied(sanction, admin, buyer, "계정 도용 의심", appliedAt));
    assertThat(activeTimers()).isEqualTo(1);

    // 판매자 보안 잠금은 구매자 검수 타이머에 영향을 주지 않는다.
    lock(seller);
    assertThat(activeTimers()).isEqualTo(1);
    // 타이머 시각을 169시간 당기므로 구매자 잠금 기록도 그보다 더 당겨 "해제 후 시작" 관계를 유지한다.
    jdbc.update(
        "UPDATE member_sanctions SET starts_at=starts_at - INTERVAL 170 HOUR,"
            + " ends_at=ends_at - INTERVAL 170 HOUR, revoked_at=revoked_at - INTERVAL 170 HOUR,"
            + " timer_stop_delivered_at=timer_stop_delivered_at - INTERVAL 170 HOUR WHERE"
            + " member_id=?",
        buyer);
    timerExpired();
    assertThat(timers.expireDue(10)).isEqualTo(1);
    assertThat(orderStatus()).isEqualTo("COMPLETED");
  }

  @Test
  void concurrentTimerStartAndBuyerLockNeverLeaveActiveTimer() throws Exception {
    paidAndDelivered();
    reviewWindowPassed();
    var results =
        parallel(
            () -> result(() -> timers.start(seller, id)),
            () -> {
              lock(buyer);
              return "LOCKED";
            });
    assertThat(results.getFirst()).isIn("OK", "BUYER_SECURITY_LOCKED");
    assertThat(activeTimers()).isZero();
    if ("OK".equals(results.getFirst())) assertThat(securityStops()).isEqualTo(1);
    else assertThat(count("order_response_timers")).isZero();
  }
}
