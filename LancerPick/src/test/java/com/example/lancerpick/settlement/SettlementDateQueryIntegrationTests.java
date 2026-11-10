package com.example.lancerpick.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderSnapshot;
import com.example.lancerpick.payment.entity.Payment;
import com.example.lancerpick.settlement.entity.Settlement;
import com.example.lancerpick.settlement.service.SettlementQueryService;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.wallet.entity.Wallet;
import com.example.lancerpick.wallet.entity.WalletEntry;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class SettlementDateQueryIntegrationTests {
  private static final LocalDateTime BASE = LocalDateTime.parse("2026-10-01T00:00:00");
  @Autowired EntityManager em;
  @Autowired JdbcTemplate jdbc;
  @Autowired SettlementQueryService queries;
  @Autowired MockMvc mvc;
  long buyer, seller, otherSeller, product, category;
  long first, last, tied;

  record Principal(Long memberId) implements AuthenticatedMember {}

  @BeforeEach
  void setup() {
    buyer = member();
    seller = member();
    otherSeller = member();
    jdbc.update(
        "INSERT INTO categories(name,display_order) VALUES (?,0)",
        "settlement-" + UUID.randomUUID());
    category = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    product = product(seller);
    settled(seller, product, "2026-11-08T14:59:59.999999", 1000);
    first = settled(seller, product, "2026-11-08T15:00:00", 2000);
    last = settled(seller, product, "2026-11-09T14:59:59.999999", 3000);
    settled(seller, product, "2026-11-09T15:00:00", 4000);
    tied = settled(seller, product, "2026-11-08T15:00:00", 5000);
    settled(otherSeller, product(otherSeller), "2026-11-09T00:00:00", 6000);
    em.flush();
  }

  long member() {
    var m = Member.create(UUID.randomUUID() + "@fixture.test", "fixture-hash", "fixture", BASE);
    em.persist(m);
    em.flush();
    return m.getId();
  }

  long product(long owner) {
    jdbc.update(
        "INSERT INTO"
            + " products(seller_id,category_id,title,description,scope_description,preparation_description,price,duration_days,created_at,updated_at)"
            + " VALUES (?,?,'fixture','desc','scope','prep',1000,1,?,?)",
        owner,
        category,
        BASE,
        BASE);
    return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  long settled(long owner, long productId, String time, long amount) {
    var at = LocalDateTime.parse(time);
    var order = Order.create(buyer, owner, productId, UUID.randomUUID(), amount, 1, BASE);
    order.accept(BASE);
    order.pay(BASE);
    order.deliver(at);
    order.confirm(at);
    em.persist(order);
    em.flush();
    var settlement = Settlement.create(order.getId(), amount, 1000, at);
    em.persist(settlement);
    em.flush();
    return settlement.getOrderId();
  }

  @Test
  void kstInclusiveDayAndSummaryUseTheSameOwnedRows() throws Exception {
    var page = queries.list(seller, 0, 20, "2026-11-09", "2026-11-09", "latest");
    assertThat(page.items())
        .extracting(x -> x.orderId())
        .containsExactly("" + last, "" + tied, "" + first);
    assertThat(page.totalElements()).isEqualTo(3);
    var summary = queries.summary(seller, "2026-11-09", "2026-11-09");
    assertThat(summary.completedCount()).isEqualTo(3);
    assertThat(summary.grossAmount()).isEqualTo(10000);
    assertThat(summary.feeAmount()).isEqualTo(1000);
    assertThat(summary.sellerAmount()).isEqualTo(9000);
    mvc.perform(
            get("/api/me/settlements")
                .with(authentication(auth(seller)))
                .param("from", "2026-11-09")
                .param("to", "2026-11-09")
                .param("memberId", "" + otherSeller))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(
            get("/api/me/settlements/summary")
                .with(authentication(auth(seller)))
                .param("from", "2026-11-09")
                .param("to", "2026-11-09"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.grossAmount").value(10000))
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void stableSortPaginationOpenBoundsAndDefaultsArePreserved() {
    assertThat(
            queries
                .list(seller, 0, 1, "2026-11-09", "2026-11-09", "oldest")
                .items()
                .getFirst()
                .orderId())
        .isEqualTo("" + first);
    assertThat(
            queries
                .list(seller, 1, 1, "2026-11-09", "2026-11-09", "oldest")
                .items()
                .getFirst()
                .orderId())
        .isEqualTo("" + tied);
    assertThat(
            queries
                .list(seller, 2, 1, "2026-11-09", "2026-11-09", "oldest")
                .items()
                .getFirst()
                .orderId())
        .isEqualTo("" + last);
    assertThat(queries.list(seller, 4, 1, "2026-11-09", "2026-11-09", "oldest").items()).isEmpty();
    assertThat(queries.list(seller, 0, 20).totalElements()).isEqualTo(5);
    assertThat(queries.summary(seller).grossAmount()).isEqualTo(15000);
    assertThat(queries.list(seller, 0, 20, "2026-11-09", null, null).totalElements()).isEqualTo(4);
    assertThat(queries.list(seller, 0, 20, null, "2026-11-08", null).totalElements()).isEqualTo(1);
    assertThat(queries.summary(buyer, "2026-11-09", "2026-11-09").completedCount()).isZero();
  }

  @Test
  void badFiltersAre400AndAnonymousCannotRead() throws Exception {
    for (var path : List.of("/api/me/settlements", "/api/me/settlements/summary")) {
      mvc.perform(get(path)).andExpect(status().isUnauthorized());
      for (var invalid :
          List.of(
              "2026-02-30", "2026-1-01", "", "2026-11-09T00:00:00", "0000-01-01", "10000-01-01")) {
        mvc.perform(get(path).with(authentication(auth(seller))).param("from", invalid))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
      }
      mvc.perform(
              get(path)
                  .with(authentication(auth(seller)))
                  .param("from", "2026-11-10")
                  .param("to", "2026-11-09"))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(
            get("/api/me/settlements").with(authentication(auth(seller))).param("sort", "price"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/me/settlements").with(authentication(auth(seller))).param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void maxDatabaseYearKeepsTheKstExclusiveUpperBoundWithoutOverflow() {
    long inside = settled(seller, product, "9999-12-31T14:59:59.999999", 1000);
    settled(seller, product, "9999-12-31T15:00:00", 2000);
    assertThat(queries.list(seller, 0, 20, "9999-12-31", "9999-12-31", null).items())
        .extracting(x -> x.orderId())
        .containsExactly("" + inside);
    assertThat(queries.list(seller, 0, 20, "1000-01-01", "9999-12-31", null).totalElements())
        .isEqualTo(6);
  }

  @Test
  void snapshotSearchAndSummaryStayOwnedLiteralAndIgnoreProductEdits() throws Exception {
    em.persist(OrderSnapshot.create(first, 0L, "로고 50%_특별!", "설명", "범위", "준비", "약관", "요구"));
    em.persist(OrderSnapshot.create(last, 0L, "로고 50XX특별!", "설명", "범위", "준비", "약관", "요구"));
    em.flush();
    jdbc.update("UPDATE products SET title='현재 상품명 변경' WHERE id=?", product);
    var page = queries.list(seller, 0, 20, "2026-11-09", "2026-11-09", null, "50%_");
    assertThat(page.totalElements()).isEqualTo(1);
    assertThat(page.items().getFirst().productTitle()).isEqualTo("로고 50%_특별!");
    assertThat(page.items().getFirst().buyerId()).isEqualTo("" + buyer);
    assertThat(queries.summary(seller, "2026-11-09", "2026-11-09", "50%_").grossAmount())
        .isEqualTo(2000);
    assertThat(queries.list(otherSeller, 0, 20, null, null, null, "" + first).totalElements())
        .isZero();
    assertThat(queries.list(seller, 0, 20, null, null, null, "" + first).totalElements())
        .isEqualTo(1);
    for (var invalidNumber : List.of("0", "99999999999999999999999999999"))
      assertThat(queries.summary(seller, null, null, invalidNumber).completedCount()).isZero();
    for (var path : List.of("/api/me/settlements", "/api/me/settlements/summary"))
      mvc.perform(get(path).with(authentication(auth(seller))).param("q", "가".repeat(101)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void payoutRequiresAnActualMatchingSellerLedgerRatherThanPolicyName() {
    assertThat(
            queries
                .list(seller, 0, 20, null, null, null, "" + first)
                .items()
                .getFirst()
                .payoutStatus())
        .isEqualTo("RECORD_ONLY");
    long funded = settled(seller, product, "2026-11-09T01:00:00", 10_000);
    jdbc.update("UPDATE orders SET policy_version='CONTRACT_V3' WHERE id=?", funded);
    var p = Payment.fullFunding(funded, 10_000, BASE);
    em.persist(Wallet.withInitialGrant(buyer, BASE));
    em.persist(Wallet.withInitialGrant(seller, BASE));
    em.persist(p);
    em.flush();
    assertThat(
            queries
                .list(seller, 0, 20, null, null, null, "" + funded)
                .items()
                .getFirst()
                .payoutStatus())
        .isEqualTo("UNCONFIRMED");
    em.persist(WalletEntry.sellerPayout(buyer, p.id(), 9000, 1_009_000, BASE));
    em.flush();
    assertThat(
            queries
                .list(seller, 0, 20, null, null, null, "" + funded)
                .items()
                .getFirst()
                .payoutStatus())
        .isEqualTo("UNCONFIRMED");
    // A forged/mismatched ledger must not become proof of the seller's credit.
    jdbc.update(
        "UPDATE wallet_entries SET member_id=?,amount_delta=8999 WHERE payment_id=?",
        seller,
        p.id());
    assertThat(
            queries
                .list(seller, 0, 20, null, null, null, "" + funded)
                .items()
                .getFirst()
                .payoutStatus())
        .isEqualTo("UNCONFIRMED");
    jdbc.update("UPDATE wallet_entries SET amount_delta=9000 WHERE payment_id=?", p.id());
    assertThat(
            queries
                .list(seller, 0, 20, null, null, null, "" + funded)
                .items()
                .getFirst()
                .payoutStatus())
        .isEqualTo("CREDITED");
    assertThat(queries.summary(seller).completedCount()).isEqualTo(6);
  }

  private UsernamePasswordAuthenticationToken auth(long id) {
    return UsernamePasswordAuthenticationToken.authenticated(new Principal(id), null, List.of());
  }
}
