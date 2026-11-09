package com.example.lancerpick.admin;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.contract.service.ContractDocumentService;
import com.example.lancerpick.contract.service.OrderContractService;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderSnapshot;
import com.example.lancerpick.order.entity.OrderTradeTerms;
import com.example.lancerpick.order.repository.OrderRepository;
import com.example.lancerpick.order.repository.OrderSnapshotRepository;
import com.example.lancerpick.order.repository.OrderTradeTermsRepository;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * K3·K6 관리자 금융·계약 PDF 조회 API 연결. 조회 규칙·후보 판단 자체는 AdminFinanceQueryIntegrationTests·
 * AdminContractPdfQuery 테스트(황준범)가 검증하고, 여기서는 실제 Security 필터·MySQL로 HTTP 경로·권한·입력 변환·no-store·민감정보
 * 비노출을 본다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class AdminFinanceApiIntegrationTests {
  static final long AMOUNT = 600001;
  static final Path directory = temp();

  static Path temp() {
    try {
      return Files.createTempDirectory("lancerpick-admin-finance-api-");
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
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @Autowired MockMvc mvc;
  long buyer, seller, other, admin, product, category, id;
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
          String name = "finance-" + UUID.randomUUID();
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
    payments.pay(buyer, Long.toString(id), true);
  }

  long member(LocalDateTime at) {
    long m =
        members
            .saveAndFlush(Member.create(UUID.randomUUID() + "@admin-finance.test", "fixture", "표시 이름", at))
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

  static final List<String> READ_PATHS =
      List.of(
          "/api/admin/finance/topups",
          "/api/admin/finance/payments",
          "/api/admin/finance/recovery-jobs");

  @Test
  void onlyCurrentAdminsCanReadAndResponsesAreNotCached() throws Exception {
    for (String path : READ_PATHS) {
      mvc.perform(get(path).with(authentication(auth(admin, "ADMIN"))))
          .andExpect(status().isOk())
          .andExpect(header().string("Cache-Control", "no-store"))
          .andExpect(jsonPath("$.items").isArray());
      // 일반 회원 세션은 Security가, 세션은 ADMIN이지만 DB 역할이 USER(강등)면 서비스가 403.
      mvc.perform(get(path).with(authentication(auth(buyer, "USER")))).andExpect(status().isForbidden());
      mvc.perform(get(path).with(authentication(auth(other, "ADMIN"))))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
    for (String path :
        List.of("/api/admin/finance/orders/" + id, "/api/admin/orders/" + id + "/contract-pdf"))
      mvc.perform(get(path).with(authentication(auth(other, "ADMIN"))))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void orderFinanceAndPaymentListShowTheHeldV3PaymentWithoutSecrets() throws Exception {
    var body =
        mvc.perform(get("/api/admin/finance/orders/" + id).with(authentication(auth(admin, "ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(Long.toString(id)))
            .andExpect(jsonPath("$.buyerId").value(Long.toString(buyer)))
            .andExpect(jsonPath("$.policyVersion").value("CONTRACT_V3"))
            .andExpect(jsonPath("$.orderAmount").value(AMOUNT))
            .andExpect(jsonPath("$.payment.status").value("PAID"))
            .andExpect(jsonPath("$.payment.amount").value(AMOUNT))
            .andExpect(jsonPath("$.funds.status").value("HELD"))
            .andExpect(jsonPath("$.funds.remainingAmount").value(AMOUNT))
            // 정산·종료 기록이 아직 없으면 0원이 아니라 null이다.
            .andExpect(jsonPath("$.settlement").doesNotExist())
            .andExpect(jsonPath("$.closure").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContainIgnoringCase("paymentKey").doesNotContain("@admin-finance.test");

    mvc.perform(
            get("/api/admin/finance/payments")
                .param("orderId", Long.toString(id))
                .param("status", "PAID")
                .param("policyVersion", "CONTRACT_V3")
                .with(authentication(auth(admin, "ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].orderId").value(Long.toString(id)))
        .andExpect(jsonPath("$.items[0].sellerId").value(Long.toString(seller)))
        .andExpect(jsonPath("$.items[0].refundedAmount").value(0));
    mvc.perform(
            get("/api/admin/finance/payments")
                .param("orderId", Long.toString(id))
                .param("status", "REFUNDED")
                .with(authentication(auth(admin, "ADMIN"))))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void contractPdfInspectionReturnsMetadataAndCandidateOnly() throws Exception {
    var body =
        mvc.perform(get("/api/admin/orders/" + id + "/contract-pdf").with(authentication(auth(admin, "ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.orderId").value(Long.toString(id)))
            .andExpect(jsonPath("$.status").value("SIGNED"))
            .andExpect(jsonPath("$.currentVersion").value(true))
            .andExpect(jsonPath("$.fileMetadataPresent").value(true))
            // 결제 후 주문은 새로 생성할 수 없으므로 후보가 아니다.
            .andExpect(jsonPath("$.generationCandidate.eligible").value(false))
            .andExpect(jsonPath("$.generationCandidate.kind").value("NONE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain("file_storage_key").doesNotContain("fileHash").doesNotContain("storageKey");
    String version = jdbc.queryForObject(
        "SELECT MAX(version) FROM order_contracts WHERE order_id=?", String.class, id);
    mvc.perform(
            get("/api/admin/orders/" + id + "/contract-pdf")
                .param("version", version)
                .with(authentication(auth(admin, "ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(Integer.parseInt(version)));
    mvc.perform(
            get("/api/admin/orders/" + id + "/contract-pdf")
                .param("version", "999")
                .with(authentication(auth(admin, "ADMIN"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void invalidFiltersAndMissingTargetsAreRejected() throws Exception {
    var bad =
        List.of(
            get("/api/admin/finance/topups").param("status", "completed"),
            get("/api/admin/finance/topups").param("status", "DONE"),
            get("/api/admin/finance/topups").param("memberId", "0"),
            get("/api/admin/finance/topups").param("size", "101"),
            get("/api/admin/finance/payments").param("status", "paid"),
            get("/api/admin/finance/payments").param("policyVersion", "V3"),
            get("/api/admin/finance/payments").param("buyerId", "-1"),
            get("/api/admin/finance/orders/0"),
            get("/api/admin/orders/abc/contract-pdf"),
            get("/api/admin/orders/" + id + "/contract-pdf").param("version", "x"));
    for (var request : bad)
      mvc.perform(request.with(authentication(auth(admin, "ADMIN"))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    for (String path :
        List.of(
            "/api/admin/finance/topups/999999999",
            "/api/admin/finance/orders/999999999",
            "/api/admin/finance/recovery-jobs/999999999",
            "/api/admin/orders/999999999/contract-pdf"))
      mvc.perform(get(path).with(authentication(auth(admin, "ADMIN"))))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }
}
