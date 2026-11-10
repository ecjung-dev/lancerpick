package com.example.lancerpick.order;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.contract.service.ContractDocumentService;
import com.example.lancerpick.contract.service.OrderContractService;
import com.example.lancerpick.inquiry.entity.SupportInquiry;
import com.example.lancerpick.inquiry.repository.SupportInquiryRepository;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.notification.service.OrderEventPublisher;
import com.example.lancerpick.notification.service.OutboxDispatcher;
import com.example.lancerpick.order.dto.CreateCancellationRequest;
import com.example.lancerpick.order.dto.CreateDeliveryRequest;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderHistory;
import com.example.lancerpick.order.entity.OrderHistoryAction;
import com.example.lancerpick.order.entity.OrderSnapshot;
import com.example.lancerpick.order.entity.OrderTradeTerms;
import com.example.lancerpick.order.repository.OrderRepository;
import com.example.lancerpick.order.repository.OrderSnapshotRepository;
import com.example.lancerpick.order.repository.OrderTradeTermsRepository;
import com.example.lancerpick.order.service.AdminCancellationReviewService;
import com.example.lancerpick.order.service.FullFundingService;
import com.example.lancerpick.order.service.OrderCancellationRequestService;
import com.example.lancerpick.order.service.OrderDeliveryService;
import com.example.lancerpick.order.service.OrderPaymentService;
import com.example.lancerpick.order.service.ReviewTimerService;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.support.V3FundsInvariant;
import com.example.lancerpick.wallet.entity.Wallet;
import com.example.lancerpick.wallet.entity.WalletEntry;
import com.example.lancerpick.wallet.repository.WalletEntryRepository;
import com.example.lancerpick.wallet.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * K4 운영 문의(취소 검토)에 대한 관리자 승인/거절. 실제 커밋·잠금으로 금액 반환·종료 기록·문의 종료가 한 트랜잭션에 묶이는지, 판매자
 * 승인·구매자 철회와 경합할 때 한쪽만 반영되는지 확인한다. 모든 검사 뒤 V3 금액 불변식을 판정한다.
 */
@IntegrationTest
class AdminCancellationReviewIntegrationTests {
  static final long AMOUNT = 600001;
  static final Path directory = temp();

  static Path temp() {
    try {
      return Files.createTempDirectory("lancerpick-admin-cancellation-");
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry p) {
    p.add("contracts.storage-root", directory::toString);
  }

  @Autowired MemberRepository members;
  @MockitoSpyBean WalletRepository wallets;
  @Autowired EntityManager entityManager;
  @Autowired WalletEntryRepository entries;
  @Autowired OrderRepository orders;
  @Autowired OrderSnapshotRepository snapshots;
  @Autowired OrderTradeTermsRepository terms;
  @Autowired OrderContractService contracts;
  @Autowired ContractDocumentService documents;
  @Autowired OrderPaymentService payments;
  @Autowired OrderDeliveryService deliveries;
  @Autowired OrderCancellationRequestService requests;
  @Autowired FullFundingService funding;
  @Autowired com.example.lancerpick.order.service.OrderConfirmationService confirmations;
  @Autowired ReviewTimerService timers;
  @Autowired SupportInquiryRepository inquiries;
  @Autowired AdminCancellationReviewService reviews;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @Autowired OutboxDispatcher dispatcher;
  @MockitoSpyBean OrderEventPublisher events;
  long buyer, seller, admin, product, category, id, requestId, inquiryId;
  final List<Long> ids = new ArrayList<>();

  @BeforeEach
  void fixture() {
    new TransactionTemplate(manager)
        .executeWithoutResult(ignored -> {
          var at = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1).truncatedTo(ChronoUnit.MICROS);
          buyer = member(at);
          seller = member(at);
          admin = member(at);
          jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", admin);
          String name = "admin-cancel-" + UUID.randomUUID();
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
    payments.pay(buyer, str(), true);
    deliveries.submit(
        seller, str(), new CreateDeliveryRequest("납품", "https://example.invalid/result", 1));
    requests.create(
        buyer, str(), new CreateCancellationRequest("판매자가 응답하지 않습니다.", UUID.randomUUID().toString()));
    requestId =
        jdbc.queryForObject(
            "SELECT id FROM cancellation_requests WHERE order_id=? AND status='PENDING'",
            Long.class,
            id);
    inquiryId = inquiry(buyer, id, requestId);
  }

  long member(LocalDateTime at) {
    long m =
        members
            .saveAndFlush(Member.create(UUID.randomUUID() + "@inquiry.test", "fixture", "표시 이름", at))
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
                      buyer, seller, product, UUID.randomUUID(), AMOUNT, 1, at,
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

  long inquiry(long requester, long order, long request) {
    var at = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    return inquiries
        .saveAndFlush(SupportInquiry.cancellationReview(requester, order, request, "판매자 무응답 취소 검토", at))
        .getId();
  }

  @AfterEach
  void cleanup() throws Exception {
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
              "support_inquiries",
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
    jdbc.update("DELETE FROM support_inquiries WHERE requester_id IN (?,?,?)", buyer, seller, admin);
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

  String str() {
    return Long.toString(id);
  }

  long balance(long m) {
    return wallets.findById(m).orElseThrow().getBalance();
  }

  Map<String, Object> row(String sql, Object... args) {
    return jdbc.queryForMap(sql, args);
  }

  long outbox(String type) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM outbox_events WHERE aggregate_type='ORDER' AND aggregate_id=? AND"
            + " type=?",
        Long.class,
        id,
        type);
  }

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
  void adminApprovalRefundsHeldFundsClosesInquiryAndNotifiesBothParties() {
    long buyerBefore = balance(buyer), sellerBefore = balance(seller);
    var result = reviews.approve(admin, inquiryId, "  판매자 7일 무응답 확인  ");

    assertThat(result.decision()).isEqualTo("APPROVED");
    assertThat(result.orderStatus()).isEqualTo("CANCELLED");
    assertThat(result.inquiryStatus()).isEqualTo("CLOSED");
    assertThat(result.inquiryId()).isEqualTo(Long.toString(inquiryId));
    assertThat(balance(buyer)).isEqualTo(buyerBefore + AMOUNT);
    assertThat(balance(seller)).isEqualTo(sellerBefore);

    var closure = row("SELECT * FROM order_closures WHERE order_id=?", id);
    assertThat(closure.get("reason")).isEqualTo("ADMIN_CANCELLATION");
    assertThat(((Number) closure.get("actor_id")).longValue()).isEqualTo(admin);
    assertThat(((Number) closure.get("inquiry_id")).longValue()).isEqualTo(inquiryId);
    assertThat(((Number) closure.get("source_cancellation_request_id")).longValue())
        .isEqualTo(requestId);
    assertThat(((Number) closure.get("buyer_refund_amount")).longValue()).isEqualTo(AMOUNT);
    assertThat(((Number) closure.get("seller_gross_amount")).longValue()).isZero();

    var request = row("SELECT * FROM cancellation_requests WHERE id=?", requestId);
    assertThat(request.get("status")).isEqualTo("APPROVED");
    assertThat(request.get("resolution_source")).isEqualTo("ADMIN");
    assertThat(((Number) request.get("resolved_by")).longValue()).isEqualTo(admin);
    assertThat(request.get("response_reason")).isEqualTo("판매자 7일 무응답 확인");

    var inquiry = row("SELECT * FROM support_inquiries WHERE id=?", inquiryId);
    assertThat(inquiry.get("status")).isEqualTo("CLOSED");
    assertThat(((Number) inquiry.get("assigned_admin_id")).longValue()).isEqualTo(admin);
    assertThat(inquiry.get("resolved_at")).isNotNull();

    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM order_funds WHERE order_id=?", String.class, id))
        .isEqualTo("REFUNDED");
    assertThat(
            jdbc.queryForList(
                "SELECT action FROM order_histories WHERE order_id=? AND actor_id=? ORDER BY id",
                String.class,
                id,
                admin))
        .containsExactly("CANCELLATION_APPROVED", "CANCELLED");
    assertThat(outbox("ORDER_FUNDS_REFUNDED")).isEqualTo(1);
    assertThat(dispatchedTypes(buyer)).contains("ORDER_FUNDS_REFUNDED");
    assertThat(dispatchedTypes(seller)).contains("ORDER_FUNDS_REFUNDED");
  }

  @Test
  void adminApprovalUsesTimeAfterWaitingForBuyerWallet() {
    long second = create();
    jdbc.update("UPDATE wallets SET balance=2000000 WHERE member_id=?", buyer);
    var intervened = new AtomicBoolean();
    var separateTransaction = new TransactionTemplate(manager);
    separateTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    // 관리자 승인의 구매자 지갑 잠금 직전에, 같은 구매자의 다른 주문 결제를 별도 트랜잭션으로 커밋해 지갑 갱신 시각을 앞당긴다.
    doAnswer(invocation -> {
      if (intervened.compareAndSet(false, true))
        separateTransaction.executeWithoutResult(ignored ->
            payments.pay(buyer, Long.toString(second), true));
      return java.util.Optional.ofNullable(
          entityManager.find(Wallet.class, buyer, LockModeType.PESSIMISTIC_WRITE));
    }).when(wallets).findByMemberIdForUpdate(buyer);

    var result = reviews.approve(admin, inquiryId, "지갑 대기 뒤 승인");

    assertThat(intervened).isTrue();
    assertThat(result.orderStatus()).isEqualTo("CANCELLED");
    assertThat(balance(buyer)).isEqualTo(2000000);
    // 주문 취소·문의 종료·지갑 갱신이 지갑 잠금 뒤의 같은 시각이고, 끼어든 결제보다 늦어야 한다.
    assertThat(
            jdbc.queryForObject(
                "SELECT o.cancelled_at=i.resolved_at AND o.cancelled_at=w.updated_at AND"
                    + " o.cancelled_at>=p.paid_at FROM orders o JOIN support_inquiries i ON i.id=?"
                    + " JOIN wallets w ON w.member_id=o.buyer_id JOIN payments p ON p.order_id=?"
                    + " WHERE o.id=?",
                Boolean.class,
                inquiryId,
                second,
                id))
        .isTrue();
  }

  @Test
  void sameDecisionReturnsResultButOtherCommandsSeeTheClosedState() {
    long refunded = balance(buyer) + AMOUNT;
    var first = reviews.approve(admin, inquiryId, "무응답 확인");
    var again = reviews.approve(admin, inquiryId, "다시 보냄");
    assertThat(again).isEqualTo(first);
    assertThat(balance(buyer)).isEqualTo(refunded);
    error(() -> reviews.reject(admin, inquiryId, "거절"), "INQUIRY_CLOSED");
    // 판매자 승인의 재전송으로 오인하지 않고 상태 충돌로 알린다.
    error(() -> funding.refund(seller, id, "판매자 승인"), "ORDER_STATE_CONFLICT");
    error(
        () ->
            requests.resolve(
                buyer,
                str(),
                Long.toString(requestId),
                OrderCancellationRequestService.Resolution.WITHDRAWN,
                null),
        "REQUEST_ALREADY_RESOLVED");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entries WHERE event_key LIKE 'REFUND:%' AND member_id=?", Long.class, buyer))
        .isEqualTo(1);
  }

  @Test
  void adminRejectionKeepsFundsHeldClosesInquiryAndRestartsReviewWindow() {
    long buyerBefore = balance(buyer);
    var result = reviews.reject(admin, inquiryId, "판매자 작업 진행 확인");

    assertThat(result.decision()).isEqualTo("REJECTED");
    assertThat(result.orderStatus()).isEqualTo("AWAITING_CONFIRMATION");
    assertThat(result.inquiryStatus()).isEqualTo("CLOSED");
    assertThat(balance(buyer)).isEqualTo(buyerBefore);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM order_funds WHERE order_id=?", String.class, id))
        .isEqualTo("HELD");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_closures WHERE order_id=?", Long.class, id))
        .isZero();
    var request = row("SELECT * FROM cancellation_requests WHERE id=?", requestId);
    assertThat(request.get("status")).isEqualTo("REJECTED");
    assertThat(request.get("resolution_source")).isEqualTo("ADMIN");
    assertThat(request.get("response_reason")).isEqualTo("판매자 작업 진행 확인");

    assertThat(outbox("ORDER_CANCELLATION_ADMIN_REJECTED")).isEqualTo(1);
    assertThat(outbox("ORDER_CANCELLATION_REJECTED")).isZero();
    assertThat(dispatchedTypes(buyer)).contains("ORDER_CANCELLATION_ADMIN_REJECTED");
    assertThat(dispatchedTypes(seller)).contains("ORDER_CANCELLATION_ADMIN_REJECTED");

    // 거절 시각이 새 검수 기산점이다. 48시간 전에는 시작할 수 없고 지나면 시작할 수 있다.
    error(() -> timers.start(seller, id), "TIMER_NOT_ELIGIBLE");
    jdbc.update(
        "UPDATE deliveries SET submitted_at=submitted_at - INTERVAL 49 HOUR WHERE order_id=?", id);
    jdbc.update(
        "UPDATE cancellation_requests SET requested_at=requested_at - INTERVAL 49 HOUR,"
            + " resolved_at=resolved_at - INTERVAL 49 HOUR WHERE order_id=?",
        id);
    assertThat(timers.start(seller, id).status()).isEqualTo("ACTIVE");
  }

  @Test
  void onlyAdminsWithValidInputCanReviewCancellationInquiries() {
    error(() -> reviews.approve(seller, inquiryId, "승인"), "FORBIDDEN");
    error(() -> reviews.approve(buyer, inquiryId, "승인"), "FORBIDDEN");
    error(() -> reviews.approve(admin, inquiryId, "   "), "VALIDATION_FAILED");
    error(() -> reviews.approve(admin, inquiryId, null), "VALIDATION_FAILED");
    error(() -> reviews.approve(admin, inquiryId, "가".repeat(1001)), "VALIDATION_FAILED");
    error(() -> reviews.approve(admin, 0, "승인"), "VALIDATION_FAILED");
    error(() -> reviews.approve(admin, Long.MAX_VALUE, "승인"), "RESOURCE_NOT_FOUND");
    error(() -> reviews.approve(Long.MAX_VALUE, inquiryId, "승인"), "AUTHENTICATION_REQUIRED");

    var at = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    long general = inquiries.saveAndFlush(SupportInquiry.general(buyer, id, "일반 문의", at)).getId();
    error(() -> reviews.approve(admin, general, "승인"), "INQUIRY_NOT_CANCELLATION_REVIEW");

    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM support_inquiries WHERE id=?", String.class, inquiryId))
        .isEqualTo("OPEN");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM cancellation_requests WHERE id=?", String.class, requestId))
        .isEqualTo("PENDING");
  }

  @Test
  void requestResolvedBySellerFirstIsReportedAndInquiryStaysOpen() {
    requests.resolve(
        seller,
        str(),
        Long.toString(requestId),
        OrderCancellationRequestService.Resolution.REJECTED,
        "작업 중입니다");
    error(() -> reviews.approve(admin, inquiryId, "승인"), "REQUEST_ALREADY_RESOLVED");
    error(() -> reviews.reject(admin, inquiryId, "거절"), "REQUEST_ALREADY_RESOLVED");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM support_inquiries WHERE id=?", String.class, inquiryId))
        .isEqualTo("OPEN");
  }

  @Test
  void failureAfterRefundRollsBackFundsRequestAndInquiry() {
    long buyerBefore = balance(buyer);
    // 트랜잭션 프록시를 거치지 않도록 스파이 대상 자체에 실패를 넣는다. 반환·종료 기록 저장 뒤 마지막 이력에서 실패한다.
    var publisher =
        org.springframework.test.util.AopTestUtils.<OrderEventPublisher>getUltimateTargetObject(
            events);
    doThrow(new IllegalStateException("주입한 실패"))
        .when(publisher)
        .publish(
            any(Order.class),
            argThat((OrderHistory h) -> h != null && h.getAction() == OrderHistoryAction.CANCELLED));
    try {
      assertThatThrownBy(() -> reviews.approve(admin, inquiryId, "승인"))
          .isInstanceOf(IllegalStateException.class);
    } finally {
      reset(publisher);
    }
    assertThat(balance(buyer)).isEqualTo(buyerBefore);
    assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id))
        .isEqualTo("AWAITING_CONFIRMATION");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM cancellation_requests WHERE id=?", String.class, requestId))
        .isEqualTo("PENDING");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM support_inquiries WHERE id=?", String.class, inquiryId))
        .isEqualTo("OPEN");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_closures WHERE order_id=?", Long.class, id))
        .isZero();
    // 실패 뒤 다시 처리하면 정상 반영된다.
    assertThat(reviews.approve(admin, inquiryId, "승인").decision()).isEqualTo("APPROVED");
  }

  @Test
  void concurrentAdminApprovalAndSellerApprovalRefundOnlyOnce() throws Exception {
    long refunded = balance(buyer) + AMOUNT;
    var outcomes =
        parallel(
            () -> result(() -> reviews.approve(admin, inquiryId, "관리자 승인")),
            () -> result(() -> funding.refund(seller, id, "판매자 승인")));
    assertThat(outcomes).containsOnlyOnce("OK");
    assertThat(outcomes)
        .anyMatch(code -> code.equals("REQUEST_ALREADY_RESOLVED") || code.equals("ORDER_STATE_CONFLICT"));
    assertThat(balance(buyer)).isEqualTo(refunded);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_closures WHERE order_id=?", Long.class, id))
        .isEqualTo(1);
  }

  @Test
  void concurrentAdminApprovalAndBuyerConfirmationNeverPaysSeller() throws Exception {
    long refunded = balance(buyer) + AMOUNT, sellerBefore = balance(seller);
    var outcomes =
        parallel(
            () -> result(() -> reviews.approve(admin, inquiryId, "관리자 승인")),
            () -> result(() -> confirmations.confirm(buyer, str())));
    // 취소 요청 대기 중에는 구매 확정이 막히고, 승인 뒤에는 취소된 주문이라 확정할 수 없다.
    assertThat(outcomes.get(0)).isEqualTo("OK");
    assertThat(outcomes.get(1)).isIn("CANCELLATION_PENDING", "ORDER_STATE_CONFLICT");
    assertThat(balance(buyer)).isEqualTo(refunded);
    assertThat(balance(seller)).isEqualTo(sellerBefore);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM settlements WHERE order_id=?", Long.class, id))
        .isZero();
  }

  @Test
  void concurrentAdminApprovalAndBuyerWithdrawalApplyOnlyOne() throws Exception {
    long before = balance(buyer);
    var outcomes =
        parallel(
            () -> result(() -> reviews.approve(admin, inquiryId, "관리자 승인")),
            () ->
                result(() ->
                    requests.resolve(
                        buyer,
                        str(),
                        Long.toString(requestId),
                        OrderCancellationRequestService.Resolution.WITHDRAWN,
                        null)));
    assertThat(outcomes).containsOnlyOnce("OK");
    String status =
        jdbc.queryForObject(
            "SELECT status FROM cancellation_requests WHERE id=?", String.class, requestId);
    if (status.equals("APPROVED")) {
      assertThat(balance(buyer)).isEqualTo(before + AMOUNT);
      assertThat(jdbc.queryForObject("SELECT status FROM support_inquiries WHERE id=?", String.class, inquiryId))
          .isEqualTo("CLOSED");
    } else {
      assertThat(status).isEqualTo("WITHDRAWN");
      assertThat(balance(buyer)).isEqualTo(before);
      assertThat(jdbc.queryForObject("SELECT status FROM support_inquiries WHERE id=?", String.class, inquiryId))
          .isEqualTo("OPEN");
    }
  }
}
