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
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.notification.service.OrderEventPublisher;
import com.example.lancerpick.order.dto.CreateCancellationRequest;
import com.example.lancerpick.order.dto.CreateDeliveryRequest;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderSnapshot;
import com.example.lancerpick.order.entity.OrderTradeTerms;
import com.example.lancerpick.order.repository.OrderRepository;
import com.example.lancerpick.order.repository.OrderSnapshotRepository;
import com.example.lancerpick.order.repository.OrderTradeTermsRepository;
import com.example.lancerpick.order.service.FullFundingService;
import com.example.lancerpick.order.service.OrderCancellationRequestService;
import com.example.lancerpick.order.service.OrderCancellationService;
import com.example.lancerpick.order.service.OrderConfirmationService;
import com.example.lancerpick.order.service.OrderDeliveryService;
import com.example.lancerpick.order.service.OrderPaymentService;
import com.example.lancerpick.order.service.OrderRevisionService;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.support.V3FundsInvariant;
import com.example.lancerpick.wallet.entity.Wallet;
import com.example.lancerpick.wallet.entity.WalletEntry;
import com.example.lancerpick.wallet.repository.WalletEntryRepository;
import com.example.lancerpick.wallet.repository.WalletRepository;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real committed transactions, independent workers, actual PDFs, no external payment calls. */
@IntegrationTest
@AutoConfigureMockMvc
class FullFundingTradeIntegrationTests {
  static final long AMOUNT = 600001;
  static final Path directory = temp();

  static Path temp() {
    try {
      return Files.createTempDirectory("lancerpick-full-trade-");
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
  @Autowired OrderCancellationService cancellations;
  @Autowired FullFundingService funding;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @Autowired MockMvc mvc;
  @MockitoSpyBean OrderEventPublisher events;
  long buyer, seller, other, product, category, id;
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
          var at =
              LocalDateTime.now(ZoneOffset.UTC)
                  .minusMinutes(1)
                  .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
          buyer = member(at);
          seller = member(at);
          other = member(at);
          String name = "full-trade-" + UUID.randomUUID();
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
            .saveAndFlush(Member.create(UUID.randomUUID() + "@full.test", "fixture", "표시 이름", at))
            .getId();
    wallets.saveAndFlush(Wallet.withInitialGrant(m, at));
    entries.saveAndFlush(WalletEntry.initialGrant(m, at));
    return m;
  }

  long create() {
    long result =
        new TransactionTemplate(manager)
            .execute(ignored -> {
              var at =
                  LocalDateTime.now(ZoneOffset.UTC)
                      .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
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
    jdbc.update("DELETE FROM products WHERE id=?", product);
    jdbc.update("DELETE FROM categories WHERE id=?", category);
    jdbc.update("DELETE FROM wallet_entries WHERE member_id IN (?,?,?)", buyer, seller, other);
    jdbc.update("DELETE FROM wallets WHERE member_id IN (?,?,?)", buyer, seller, other);
    jdbc.update("DELETE FROM members WHERE id IN (?,?,?)", buyer, seller, other);
    assertThat(violations).as("V3 금액 불변식 위반").isEmpty();
  }

  @AfterAll
  static void removeDirectory() throws Exception {
    try (var files = Files.list(directory)) {
      for (var p : files.toList()) Files.delete(p);
    }
    Files.delete(directory);
  }

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

  void deliver() {
    deliveries.submit(
        seller, str(), new CreateDeliveryRequest("납품", "https://example.invalid/result", 1));
  }

  @Test
  void httpPaymentIgnoresClientMoneyAndCompletionPaysSellerExactlyOnce() throws Exception {
    mvc.perform(
            post("/api/orders/" + id + "/payment")
                .with(authentication(auth(buyer)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agreedToTerms\":true,\"amount\":1,\"memberId\":" + other + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    payments.pay(buyer, str(), true);
    assertThat(balance(buyer)).isEqualTo(1000000 - AMOUNT);
    assertThat(count("payments")).isEqualTo(1);
    assertThat(funding.summary(buyer, id).heldAmount()).isEqualTo(AMOUNT);
    deliver();
    confirmations.confirm(buyer, str());
    confirmations.confirm(buyer, str());
    assertThat(balance(seller)).isEqualTo(1000000 + 540001);
    assertThat(count("settlements")).isEqualTo(1);
    assertThat(count("order_closures")).isEqualTo(1);
    assertThat(funding.summary(seller, id).sellerPayout()).isEqualTo(540001L);
    assertThat(funding.summary(seller, id).heldAmount()).isZero();
    mvc.perform(get("/api/orders/" + id + "/funds").with(authentication(auth(other))))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/orders/" + id + "/funds")).andExpect(status().isUnauthorized());
  }

  @Test
  void approvedCancellationRefundsOnceWithoutSellerIncome() {
    payments.pay(buyer, str(), true);
    error(() -> cancellations.cancel(seller, str(), "cancel"), "CANCELLATION_REQUEST_REQUIRED");
    requests.create(
        buyer, str(), new CreateCancellationRequest("취소 요청", UUID.randomUUID().toString()));
    long request =
        jdbc.queryForObject(
            "SELECT id FROM cancellation_requests WHERE order_id=?", Long.class, id);
    requests.resolve(
        seller,
        str(),
        Long.toString(request),
        OrderCancellationRequestService.Resolution.APPROVED,
        null);
    requests.resolve(
        seller,
        str(),
        Long.toString(request),
        OrderCancellationRequestService.Resolution.APPROVED,
        null);
    assertThat(balance(buyer)).isEqualTo(1000000);
    assertThat(balance(seller)).isEqualTo(1000000);
    assertThat(count("settlements")).isZero();
    assertThat(count("order_closures")).isEqualTo(1);
    assertThat(funding.summary(buyer, id).status()).isEqualTo("REFUNDED");
    error(() -> payments.pay(buyer, str(), true), "PAYMENT_ALREADY_REFUNDED");
  }

  @Test
  void simultaneousPaymentsUseSharedWalletWithoutOverspending() throws Exception {
    long second = create();
    var results = parallel(() -> payResult(id), () -> payResult(second));
    assertThat(results).containsExactlyInAnyOrder("OK", "INSUFFICIENT_BALANCE");
    assertThat(balance(buyer)).isEqualTo(1000000 - AMOUNT);
  }

  @Test
  void repeatedConcurrentPaymentsCreateOneDebit() throws Exception {
    assertThat(parallel(() -> payResult(id), () -> payResult(id))).containsOnly("OK");
    assertThat(count("payments")).isEqualTo(1);
    assertThat(balance(buyer)).isEqualTo(1000000 - AMOUNT);
  }

  @Test
  void payoutFailureRollsBackWalletLedgerClosureAndOrderTogether() {
    payments.pay(buyer, str(), true);
    deliver();
    var publisher =
        org.springframework.test.util.AopTestUtils.<OrderEventPublisher>getUltimateTargetObject(
            events);
    doThrow(new IllegalStateException("outbox unavailable")).when(publisher).publish(any(), any());
    assertThatThrownBy(() -> confirmations.confirm(buyer, str()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(balance(seller)).isEqualTo(1000000);
    assertThat(count("settlements")).isZero();
    assertThat(count("order_closures")).isZero();
    assertThat(funding.summary(buyer, id).status()).isEqualTo("HELD");
    assertThat(orders.findById(id).orElseThrow().getStatus().name())
        .isEqualTo("AWAITING_CONFIRMATION");
  }

  @Test
  void pendingCancellationBlocksConfirmationAndWrongActorsCannotMoveFunds() {
    error(() -> payments.pay(seller, str(), true), "FORBIDDEN");
    payments.pay(buyer, str(), true);
    deliver();
    requests.create(
        buyer, str(), new CreateCancellationRequest("취소 요청", UUID.randomUUID().toString()));
    error(() -> confirmations.confirm(buyer, str()), "CANCELLATION_PENDING");
    error(() -> confirmations.confirm(seller, str()), "FORBIDDEN");
    error(() -> cancellations.cancel(buyer, str(), "cancel"), "FORBIDDEN");
    assertThat(funding.summary(buyer, id).status()).isEqualTo("HELD");
  }

  @Test
  void paymentFailureRollsBackDebitPaymentAndHolding() {
    failPublishing();
    assertThatThrownBy(() -> payments.pay(buyer, str(), true))
        .isInstanceOf(IllegalStateException.class);
    assertThat(balance(buyer)).isEqualTo(1000000);
    assertThat(count("payments")).isZero();
    assertThat(count("order_funds")).isZero();
    assertThat(orders.findById(id).orElseThrow().getStatus().name()).isEqualTo("AWAITING_PAYMENT");
  }

  @Test
  void refundFailureRollsBackRequestApprovalWalletAndClosure() {
    payments.pay(buyer, str(), true);
    requests.create(
        buyer, str(), new CreateCancellationRequest("취소", UUID.randomUUID().toString()));
    long request =
        jdbc.queryForObject(
            "SELECT id FROM cancellation_requests WHERE order_id=?", Long.class, id);
    failPublishing();
    assertThatThrownBy(() ->
            requests.resolve(
                seller,
                str(),
                Long.toString(request),
                OrderCancellationRequestService.Resolution.APPROVED,
                null))
        .isInstanceOf(IllegalStateException.class);
    assertThat(balance(buyer)).isEqualTo(1000000 - AMOUNT);
    assertThat(count("order_closures")).isZero();
    assertThat(funding.summary(buyer, id).status()).isEqualTo("HELD");
    assertThat(
            jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?", String.class, id))
        .isEqualTo("PAID");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM cancellation_requests WHERE id=?", String.class, request))
        .isEqualTo("PENDING");
  }

  @Test
  void simultaneousConfirmationsCreditSellerOnlyOnce() throws Exception {
    payments.pay(buyer, str(), true);
    deliver();
    assertThat(
            parallel(
                () -> result(() -> confirmations.confirm(buyer, str())),
                () -> result(() -> confirmations.confirm(buyer, str()))))
        .containsOnly("OK");
    assertThat(balance(seller)).isEqualTo(1540001);
    assertThat(count("settlements")).isEqualTo(1);
    assertThat(count("order_closures")).isEqualTo(1);
  }

  @Test
  void cancellationAndConfirmationCannotBothWin() throws Exception {
    payments.pay(buyer, str(), true);
    deliver();
    var results =
        parallel(
            () ->
                result(() ->
                    requests.create(
                        buyer,
                        str(),
                        new CreateCancellationRequest("취소", UUID.randomUUID().toString()))),
            () -> result(() -> confirmations.confirm(buyer, str())));
    assertThat(results.stream().filter("OK"::equals).count()).isEqualTo(1);
    if ("COMPLETED".equals(orders.findById(id).orElseThrow().getStatus().name())) {
      assertThat(count("cancellation_requests")).isZero();
      assertThat(balance(seller)).isEqualTo(1540001);
      assertThat(funding.summary(buyer, id).status()).isEqualTo("RELEASED");
    } else {
      assertThat(results).contains("CANCELLATION_PENDING");
      assertThat(count("cancellation_requests")).isEqualTo(1);
      assertThat(balance(seller)).isEqualTo(1000000);
      assertThat(funding.summary(buyer, id).status()).isEqualTo("HELD");
    }
  }

  @Test
  void missingSignatureCannotDebitEvenIfOrderSaysAwaitingPayment() {
    jdbc.update("DELETE FROM contract_signatures WHERE member_id=?", buyer);
    error(() -> payments.pay(buyer, str(), true), "CONTRACT_NOT_SIGNED");
    assertThat(balance(buyer)).isEqualTo(1000000);
    assertThat(count("payments")).isZero();
  }

  void failPublishing() {
    var publisher =
        org.springframework.test.util.AopTestUtils.<OrderEventPublisher>getUltimateTargetObject(
            events);
    doThrow(new IllegalStateException("outbox unavailable")).when(publisher).publish(any(), any());
  }

  String result(Runnable action) {
    try {
      action.run();
      return "OK";
    } catch (BusinessException e) {
      return e.code();
    }
  }

  String payResult(long order) {
    try {
      payments.pay(buyer, Long.toString(order), true);
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
}
