package com.example.lancerpick.inquiry;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.contract.service.ContractDocumentService;
import com.example.lancerpick.contract.service.OrderContractService;
import com.example.lancerpick.inquiry.service.InquiryService;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.order.dto.CreateCancellationRequest;
import com.example.lancerpick.order.dto.CreateDeliveryRequest;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderSnapshot;
import com.example.lancerpick.order.entity.OrderTradeTerms;
import com.example.lancerpick.order.repository.OrderRepository;
import com.example.lancerpick.order.repository.OrderSnapshotRepository;
import com.example.lancerpick.order.repository.OrderTradeTermsRepository;
import com.example.lancerpick.order.service.OrderCancellationRequestService;
import com.example.lancerpick.order.service.OrderDeliveryService;
import com.example.lancerpick.order.service.OrderPaymentService;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.support.V3FundsInvariant;
import com.example.lancerpick.wallet.entity.Wallet;
import com.example.lancerpick.wallet.entity.WalletEntry;
import com.example.lancerpick.wallet.repository.WalletEntryRepository;
import com.example.lancerpick.wallet.repository.WalletRepository;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * K4 운영 문의 연결: 구매자 취소 검토 요청 API와 관리자 문의 목록·상세·승인/거절·종료 API. 실제 Security 필터·MySQL 커밋·잠금으로
 * 검사한다. 금액 처리 자체는 AdminCancellationReviewIntegrationTests가 검증하고, 여기서는 HTTP 연결·권한·중복 방지·종료 경로를 본다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class CancellationReviewInquiryIntegrationTests {
  static final long AMOUNT = 600001;
  static final Path directory = temp();

  static Path temp() {
    try {
      return Files.createTempDirectory("lancerpick-review-inquiry-");
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
  @Autowired OrderDeliveryService deliveries;
  @Autowired OrderCancellationRequestService requests;
  @Autowired InquiryService inquiryService;
  @Autowired com.example.lancerpick.order.service.ReviewTimerService reviewTimers;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @Autowired MockMvc mvc;
  long buyer, seller, other, admin, product, category, id, requestId;
  final List<Long> ids = new ArrayList<>();

  record Principal(Long memberId) implements AuthenticatedMember {}

  static UsernamePasswordAuthenticationToken auth(long memberId, String role) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new Principal(memberId), null, AuthorityUtils.createAuthorityList("ROLE_" + role));
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
          // categories.name은 VARCHAR(50): 접두어 8자 + UUID 36자 = 44자.
          String name = "inquiry-" + UUID.randomUUID();
          jdbc.update("INSERT INTO categories(name,display_order) VALUES (?,0)", name);
          category = jdbc.queryForObject("SELECT id FROM categories WHERE name=?", Long.class, name);
          jdbc.update(
              "INSERT INTO"
                  + " products(seller_id,category_id,title,description,scope_description,preparation_description,price,duration_days,created_at,updated_at)"
                  + " VALUES (?,?,'상품','설명','범위','준비',?,1,?,?)",
              seller,
              category,
              AMOUNT,
              at,
              at);
          product = jdbc.queryForObject("SELECT id FROM products WHERE seller_id=?", Long.class, seller);
        });
    id = create();
    payments.pay(buyer, str(), true);
    deliveries.submit(seller, str(), new CreateDeliveryRequest("납품", "https://example.invalid/result", 1));
    requests.create(
        buyer, str(), new CreateCancellationRequest("판매자가 응답하지 않습니다.", UUID.randomUUID().toString()));
    requestId =
        jdbc.queryForObject(
            "SELECT id FROM cancellation_requests WHERE order_id=? AND status='PENDING'", Long.class, id);
  }

  long member(LocalDateTime at) {
    long m =
        members
            .saveAndFlush(Member.create(UUID.randomUUID() + "@review-inquiry.test", "fixture", "표시 이름", at))
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
                      buyer, seller, product, UUID.randomUUID(), AMOUNT, 1, at, TradePolicyVersion.CONTRACT_V3);
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
    var violations = V3FundsInvariant.violations(jdbc, ids);
    for (long order : ids) {
      var keys =
          jdbc.queryForList(
              "SELECT file_storage_key FROM order_contracts WHERE order_id=? AND file_storage_key IS NOT NULL",
              String.class,
              order);
      jdbc.update("DELETE FROM notifications WHERE order_id=?", order);
      jdbc.update("DELETE FROM outbox_events WHERE aggregate_type='ORDER' AND aggregate_id=?", order);
      for (String table : List.of("order_histories", "order_closures", "order_funds", "settlements"))
        jdbc.update("DELETE FROM " + table + " WHERE order_id=?", order);
      jdbc.update(
          "DELETE FROM wallet_entries WHERE payment_id IN (SELECT id FROM payments WHERE order_id=?)", order);
      for (String table :
          List.of(
              "payments",
              "support_inquiries",
              "cancellation_requests",
              "order_response_timers",
              "revision_requests",
              "deliveries")) jdbc.update("DELETE FROM " + table + " WHERE order_id=?", order);
      jdbc.update(
          "DELETE FROM contract_signatures WHERE contract_id IN (SELECT id FROM order_contracts WHERE order_id=?)",
          order);
      for (String table : List.of("order_contracts", "order_trade_terms", "order_snapshots"))
        jdbc.update("DELETE FROM " + table + " WHERE order_id=?", order);
      jdbc.update("DELETE FROM orders WHERE id=?", order);
      for (String key : keys) Files.deleteIfExists(directory.resolve(key));
    }
    jdbc.update("DELETE FROM products WHERE id=?", product);
    jdbc.update("DELETE FROM categories WHERE id=?", category);
    for (long m : List.of(buyer, seller, other, admin)) {
      jdbc.update("DELETE FROM wallet_entries WHERE member_id=?", m);
      jdbc.update("DELETE FROM wallets WHERE member_id=?", m);
      jdbc.update("DELETE FROM members WHERE id=?", m);
    }
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

  String reviewPath() {
    return "/api/orders/" + id + "/cancellation-requests/" + requestId + "/review-inquiries";
  }

  static String json(String field, String value) {
    return "{\"" + field + "\":\"" + value + "\"}";
  }

  long inquiryCount() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM support_inquiries WHERE order_id=?", Long.class, id);
  }

  long submitAsBuyer() {
    return Long.parseLong(
        inquiryService.requestCancellationReview(buyer, id, requestId, "판매자 무응답 검토 요청").inquiry().id());
  }

  /**
   * K5 검사용 활성 타이머: 대기 취소 요청을 구매자가 철회하고(새 기산점), 납품·철회 시각을 49시간 전으로 옮긴 뒤 판매자가 시작한다
   * (ReviewTimerIntegrationTests와 같은 방식, DB CHECK의 시각 관계는 유지된다).
   */
  void startTimer() {
    requests.resolve(
        buyer, str(), Long.toString(requestId), OrderCancellationRequestService.Resolution.WITHDRAWN, null);
    jdbc.update("UPDATE deliveries SET submitted_at=submitted_at - INTERVAL 49 HOUR WHERE order_id=?", id);
    jdbc.update(
        "UPDATE cancellation_requests SET requested_at=requested_at - INTERVAL 49 HOUR,"
            + " resolved_at=resolved_at - INTERVAL 49 HOUR WHERE order_id=? AND resolved_at IS NOT NULL",
        id);
    assertThat(reviewTimers.start(seller, id).status()).isEqualTo("ACTIVE");
  }

  // ---------- tests ----------

  @Test
  void buyerRequestsReviewOnceAndAdminSeesTheCancellationContext() throws Exception {
    mvc.perform(
            post(reviewPath())
                .with(authentication(auth(buyer, "USER")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("subject", "  판매자가 사흘째 응답이 없습니다  ")))
        .andExpect(status().isCreated())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.type").value("CANCELLATION_REVIEW"))
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.subject").value("판매자가 사흘째 응답이 없습니다"))
        .andExpect(jsonPath("$.orderId").value(str()))
        .andExpect(jsonPath("$.cancellationRequestId").value(Long.toString(requestId)));
    long inquiryId =
        jdbc.queryForObject("SELECT id FROM support_inquiries WHERE order_id=?", Long.class, id);
    var row = jdbc.queryForMap("SELECT * FROM support_inquiries WHERE id=?", inquiryId);
    assertThat(row.get("requester_id")).isEqualTo(buyer);
    assertThat(row.get("assigned_admin_id")).isNull();

    // 재전송: 새 문의를 만들지 않고 같은 문의를 200으로 돌려준다.
    mvc.perform(
            post(reviewPath())
                .with(authentication(auth(buyer, "USER")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("subject", "다시 누름")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(Long.toString(inquiryId)))
        .andExpect(jsonPath("$.subject").value("판매자가 사흘째 응답이 없습니다"));
    assertThat(inquiryCount()).isEqualTo(1);

    mvc.perform(get("/api/me/inquiries").param("orderId", str()).with(authentication(auth(buyer, "USER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(Long.toString(inquiryId)));
    // 다른 회원에게는 보이지 않는다.
    mvc.perform(get("/api/me/inquiries").param("orderId", str()).with(authentication(auth(seller, "USER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));

    mvc.perform(
            get("/api/admin/inquiries")
                .param("status", "OPEN")
                .param("type", "CANCELLATION_REVIEW")
                .param("size", "100")
                .with(authentication(auth(admin, "ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.items[?(@.id=='" + inquiryId + "')].requester.id").value(Long.toString(buyer)));
    mvc.perform(get("/api/admin/inquiries/" + inquiryId).with(authentication(auth(admin, "ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.order.id").value(str()))
        .andExpect(jsonPath("$.order.priceAmount").value(AMOUNT))
        .andExpect(jsonPath("$.order.buyer.id").value(Long.toString(buyer)))
        .andExpect(jsonPath("$.order.seller.id").value(Long.toString(seller)))
        .andExpect(jsonPath("$.cancellationRequest.status").value("PENDING"))
        .andExpect(jsonPath("$.cancellationRequest.reason").value("판매자가 응답하지 않습니다."))
        .andExpect(jsonPath("$.allowedActions[0]").value("APPROVE"))
        .andExpect(jsonPath("$.allowedActions[1]").value("REJECT"));
  }

  @Test
  void onlyTheBuyerOfAPendingPaidV3RequestCanAskForReview() throws Exception {
    var body = json("subject", "검토 요청");
    // CSRF 없음
    mvc.perform(
            post(reviewPath())
                .with(authentication(auth(buyer, "USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    // 판매자 403, 주문 당사자가 아닌 회원 404, 없는 취소 요청 404
    mvc.perform(
            post(reviewPath())
                .with(authentication(auth(seller, "USER")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mvc.perform(
            post(reviewPath())
                .with(authentication(auth(other, "USER")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    mvc.perform(
            post("/api/orders/" + id + "/cancellation-requests/" + (requestId + 100000) + "/review-inquiries")
                .with(authentication(auth(buyer, "USER")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound());
    // 제목: 공백만·151자·숫자 타입은 400
    for (String bad : List.of("{\"subject\":\"   \"}", "{\"subject\":\"" + "가".repeat(151) + "\"}", "{\"subject\":3}", "{}"))
      mvc.perform(
              post(reviewPath())
                  .with(authentication(auth(buyer, "USER")))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(bad))
          .andExpect(status().isBadRequest());
    assertThat(inquiryCount()).isZero();

    // 구매자가 철회한 요청은 409
    requests.resolve(
        buyer, str(), Long.toString(requestId), OrderCancellationRequestService.Resolution.WITHDRAWN, null);
    mvc.perform(
            post(reviewPath())
                .with(authentication(auth(buyer, "USER")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REQUEST_ALREADY_RESOLVED"));
    assertThat(inquiryCount()).isZero();
  }

  @Test
  void concurrentRequestsForTheSameCancellationCreateOneInquiry() throws Exception {
    int workers = 4;
    var ready = new CountDownLatch(workers);
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(workers);
    try {
      List<Future<InquiryService.Submitted>> results = new ArrayList<>();
      for (int n = 0; n < workers; n++) {
        Callable<InquiryService.Submitted> task =
            () -> {
              ready.countDown();
              start.await();
              return inquiryService.requestCancellationReview(buyer, id, requestId, "동시 요청");
            };
        results.add(pool.submit(task));
      }
      ready.await(10, TimeUnit.SECONDS);
      start.countDown();
      var ids = new java.util.HashSet<String>();
      int created = 0;
      for (var result : results) {
        var submitted = result.get(30, TimeUnit.SECONDS);
        ids.add(submitted.inquiry().id());
        if (submitted.created()) created++;
      }
      assertThat(created).isEqualTo(1);
      assertThat(ids).hasSize(1);
      assertThat(inquiryCount()).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void adminApprovesThroughTheApiAndTheInquiryIsClosed() throws Exception {
    long inquiryId = submitAsBuyer();
    // 세션 역할이 USER면 Security가 막는다.
    mvc.perform(
            post("/api/admin/inquiries/" + inquiryId + "/approve")
                .with(authentication(auth(buyer, "USER")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("reason", "승인")))
        .andExpect(status().isForbidden());
    // 세션은 ADMIN이지만 DB 역할이 USER(강등)면 서비스가 403.
    mvc.perform(get("/api/admin/inquiries/" + inquiryId).with(authentication(auth(other, "ADMIN"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mvc.perform(
            post("/api/admin/inquiries/" + inquiryId + "/approve")
                .with(authentication(auth(other, "ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("reason", "승인")))
        .andExpect(status().isForbidden());
    // CSRF 없음
    mvc.perform(
            post("/api/admin/inquiries/" + inquiryId + "/approve")
                .with(authentication(auth(admin, "ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("reason", "승인")))
        .andExpect(status().isForbidden());
    // 사유 공백은 400
    mvc.perform(
            post("/api/admin/inquiries/" + inquiryId + "/approve")
                .with(authentication(auth(admin, "ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("reason", "  ")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    assertThat(jdbc.queryForObject("SELECT status FROM support_inquiries WHERE id=?", String.class, inquiryId))
        .isEqualTo("OPEN");

    mvc.perform(
            post("/api/admin/inquiries/" + inquiryId + "/approve")
                .with(authentication(auth(admin, "ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("reason", "판매자 무응답 확인")))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.decision").value("APPROVED"))
        .andExpect(jsonPath("$.orderStatus").value("CANCELLED"))
        .andExpect(jsonPath("$.inquiryStatus").value("CLOSED"));
    mvc.perform(get("/api/admin/inquiries/" + inquiryId).with(authentication(auth(admin, "ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CLOSED"))
        .andExpect(jsonPath("$.assignedAdminId").value(Long.toString(admin)))
        .andExpect(jsonPath("$.cancellationRequest.status").value("APPROVED"))
        .andExpect(jsonPath("$.cancellationRequest.resolutionSource").value("ADMIN"))
        .andExpect(jsonPath("$.allowedActions").isEmpty());
    mvc.perform(get("/api/me/inquiries").param("orderId", str()).with(authentication(auth(buyer, "USER"))))
        .andExpect(jsonPath("$.items[0].status").value("CLOSED"));
  }

  @Test
  void adminRejectsThroughTheApi() throws Exception {
    long inquiryId = submitAsBuyer();
    mvc.perform(
            post("/api/admin/inquiries/" + inquiryId + "/reject")
                .with(authentication(auth(admin, "ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("reason", "작업 진행이 확인됨")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decision").value("REJECTED"))
        .andExpect(jsonPath("$.inquiryStatus").value("CLOSED"));
    assertThat(jdbc.queryForObject("SELECT status FROM cancellation_requests WHERE id=?", String.class, requestId))
        .isEqualTo("REJECTED");
  }

  @Test
  void inquiryLeftOpenAfterTheSellerActedFirstCanOnlyBeClosed() throws Exception {
    long inquiryId = submitAsBuyer();
    // 요청이 대기 중이면 종료가 아니라 승인/거절로 처리해야 한다.
    mvc.perform(
            post("/api/admin/inquiries/" + inquiryId + "/close")
                .with(authentication(auth(admin, "ADMIN")))
                .with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REQUEST_STILL_PENDING"));

    requests.resolve(
        seller, str(), Long.toString(requestId), OrderCancellationRequestService.Resolution.REJECTED, "작업 중입니다");
    mvc.perform(
            post("/api/admin/inquiries/" + inquiryId + "/approve")
                .with(authentication(auth(admin, "ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("reason", "승인")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REQUEST_ALREADY_RESOLVED"));
    mvc.perform(get("/api/admin/inquiries/" + inquiryId).with(authentication(auth(admin, "ADMIN"))))
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.cancellationRequest.status").value("REJECTED"))
        .andExpect(jsonPath("$.allowedActions[0]").value("CLOSE"));
    // 판매자만 거절 사유를 남긴 상태에서 관리자가 문의를 닫는다(금액·주문 변화 없음).
    String orderStatus = jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id);
    mvc.perform(
            post("/api/admin/inquiries/" + inquiryId + "/close")
                .with(authentication(auth(admin, "ADMIN"))))
        .andExpect(status().isForbidden()); // CSRF 없음
    mvc.perform(
            post("/api/admin/inquiries/" + inquiryId + "/close")
                .with(authentication(auth(admin, "ADMIN")))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CLOSED"))
        .andExpect(jsonPath("$.assignedAdminId").value(Long.toString(admin)))
        .andExpect(jsonPath("$.allowedActions").isEmpty());
    // 재전송은 같은 결과
    mvc.perform(
            post("/api/admin/inquiries/" + inquiryId + "/close")
                .with(authentication(auth(admin, "ADMIN")))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CLOSED"));
    assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id)).isEqualTo(orderStatus);
    assertThat(jdbc.queryForObject("SELECT resolved_at FROM support_inquiries WHERE id=?", LocalDateTime.class, inquiryId))
        .isNotNull();
  }

  @Test
  void adminSeesActiveTimersAndStopsOneThroughTheApi() throws Exception {
    startTimer();
    String mine = "$.items[?(@.orderId=='" + id + "')]";
    mvc.perform(get("/api/admin/review-timers").param("size", "100").with(authentication(auth(admin, "ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath(mine + ".buyer.id").value(Long.toString(buyer)))
        .andExpect(jsonPath(mine + ".seller.id").value(Long.toString(seller)))
        .andExpect(jsonPath(mine + ".cycleNo").value(1));
    // 세션은 ADMIN이지만 DB 역할이 USER(강등)면 조회도 해제도 403.
    mvc.perform(get("/api/admin/review-timers").with(authentication(auth(other, "ADMIN"))))
        .andExpect(status().isForbidden());
    String stop = "/api/admin/orders/" + id + "/review-timer/stop";
    mvc.perform(
            post(stop)
                .with(authentication(auth(other, "ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("reason", "해제")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    // 일반 회원 세션은 Security가 막는다. CSRF 없음도 403.
    mvc.perform(
            post(stop)
                .with(authentication(auth(buyer, "USER")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("reason", "해제")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(stop)
                .with(authentication(auth(admin, "ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("reason", "해제")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(stop)
                .with(authentication(auth(admin, "ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("reason", "   ")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    assertThat(jdbc.queryForObject(
            "SELECT status FROM order_response_timers WHERE order_id=? ORDER BY id DESC LIMIT 1", String.class, id))
        .isEqualTo("ACTIVE");

    mvc.perform(
            post(stop)
                .with(authentication(auth(admin, "ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("reason", "운영 문의 확인 중")))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.status").value("ADMIN_STOPPED"));
    var timer =
        jdbc.queryForMap(
            "SELECT status,end_reason,ended_by,admin_stop_reason FROM order_response_timers WHERE order_id=?"
                + " ORDER BY id DESC LIMIT 1",
            id);
    assertThat(timer.get("status")).isEqualTo("CANCELLED");
    assertThat(timer.get("end_reason")).isEqualTo("ADMIN_STOP");
    assertThat(timer.get("ended_by")).isEqualTo(admin);
    assertThat(timer.get("admin_stop_reason")).isEqualTo("운영 문의 확인 중");
    mvc.perform(get("/api/admin/review-timers").param("size", "100").with(authentication(auth(admin, "ADMIN"))))
        .andExpect(jsonPath(mine).isEmpty());
    // 이미 해제된 주문·없는 주문
    mvc.perform(
            post(stop)
                .with(authentication(auth(admin, "ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("reason", "다시")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TIMER_NOT_ACTIVE"));
    mvc.perform(
            post("/api/admin/orders/999999999/review-timer/stop")
                .with(authentication(auth(admin, "ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("reason", "해제")))
        .andExpect(status().isNotFound());
  }

  @Test
  void adminListRejectsUnknownFiltersAndMissingInquiries() throws Exception {
    mvc.perform(get("/api/admin/inquiries").param("status", "open").with(authentication(auth(admin, "ADMIN"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    mvc.perform(get("/api/admin/inquiries").param("type", "OTHER").with(authentication(auth(admin, "ADMIN"))))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/admin/inquiries/999999999").with(authentication(auth(admin, "ADMIN"))))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/admin/inquiries/999999999/close")
                .with(authentication(auth(admin, "ADMIN")))
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertThatThrownBy(() -> inquiryService.requestCancellationReview(buyer, id, requestId, null))
        .isInstanceOf(BusinessException.class);
  }
}
