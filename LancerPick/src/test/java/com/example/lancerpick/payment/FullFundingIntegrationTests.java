package com.example.lancerpick.payment;

import static org.assertj.core.api.Assertions.*;

import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.contract.service.OrderContractService;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.order.entity.*;
import com.example.lancerpick.order.repository.*;
import com.example.lancerpick.payment.entity.*;
import com.example.lancerpick.payment.repository.*;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.wallet.entity.*;
import com.example.lancerpick.wallet.repository.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class FullFundingIntegrationTests {
  @Autowired MemberRepository members;
  @Autowired OrderRepository orders;
  @Autowired OrderSnapshotRepository snapshots;
  @Autowired OrderTradeTermsRepository terms;
  @Autowired OrderContractService contracts;
  @Autowired PaymentRepository payments;
  @Autowired OrderFundsRepository funds;
  @Autowired WalletRepository wallets;
  @Autowired WalletEntryRepository entries;
  @Autowired JdbcTemplate jdbc;
  long buyer, seller, product;
  final LocalDateTime at = LocalDateTime.of(2026, 11, 1, 0, 0);

  @BeforeEach
  void fixture() {
    buyer =
        members
            .saveAndFlush(Member.create(UUID.randomUUID() + "@funds.test", "fixture", "구매자", at))
            .getId();
    seller =
        members
            .saveAndFlush(Member.create(UUID.randomUUID() + "@funds.test", "fixture", "판매자", at))
            .getId();
    String name = "funds-" + UUID.randomUUID();
    jdbc.update("INSERT INTO categories(name,display_order) VALUES (?,0)", name);
    long category = jdbc.queryForObject("SELECT id FROM categories WHERE name=?", Long.class, name);
    jdbc.update(
        "INSERT INTO"
            + " products(seller_id,category_id,title,description,scope_description,preparation_description,price,duration_days,created_at,updated_at)"
            + " VALUES (?,?,'상품','설명','범위','준비',10001,1,?,?)",
        seller,
        category,
        at,
        at);
    product = jdbc.queryForObject("SELECT id FROM products WHERE seller_id=?", Long.class, seller);
  }

  private com.example.lancerpick.order.entity.Order order(TradePolicyVersion policy) {
    var o =
        com.example.lancerpick.order.entity.Order.create(
            buyer, seller, product, UUID.randomUUID(), 10001L, 1, at, policy);
    o.accept(at);
    orders.saveAndFlush(o);
    snapshots.saveAndFlush(
        OrderSnapshot.create(o.getId(), 0L, "상품", "설명", "범위", "준비", "조건", "요구사항"));
    terms.saveAndFlush(
        policy == TradePolicyVersion.CONTRACT_V3
            ? OrderTradeTerms.fullFunding(o.getId())
            : OrderTradeTerms.contractV2(o.getId()));
    return o;
  }

  @Test
  void newTemplateUsesFullFundingAndDoesNotRewriteV2Snapshot() {
    var old = order(TradePolicyVersion.CONTRACT_V2);
    var oldDraft = contracts.prepare(seller, old.getId(), 0, "");
    var current = order(TradePolicyVersion.CONTRACT_V3);
    var draft = contracts.prepare(seller, current.getId(), 0, "추가 특약");
    assertThat(draft.canonicalContent())
        .contains("FULL_HOLD", "standard-v2-full-funding", "자동 구매 확정")
        .doesNotContain("\"deposit\"", "\"balance\"");
    assertThat(terms.findById(current.getId()).orElseThrow().getFundingMode())
        .isEqualTo("FULL_HOLD");
    assertThat(contracts.prepare(seller, old.getId(), 0, "").contentHash())
        .isEqualTo(oldDraft.contentHash());
  }

  @Test
  void fundsAreBoundToTheSamePaymentAndOrderAndResolveOnce() {
    var o = order(TradePolicyVersion.CONTRACT_V3);
    var payment = payments.saveAndFlush(Payment.fullFunding(o.getId(), 10001, at));
    var held = funds.saveAndFlush(OrderFunds.hold(o.getId(), payment.id(), 10001, at));
    assertThat(held.getRemainingAmount()).isEqualTo(10001);
    held.release(at);
    funds.flush();
    assertThatThrownBy(() -> held.refund(at)).isInstanceOf(IllegalStateException.class);
    assertThat(held.getStatus()).isEqualTo("RELEASED");
  }

  @Test
  void payoutLedgerIsUniqueAndUsesPurchaseWalletWithoutAdminAccount() {
    var o = order(TradePolicyVersion.CONTRACT_V3);
    var payment = payments.saveAndFlush(Payment.fullFunding(o.getId(), 10001, at));
    var wallet = wallets.saveAndFlush(Wallet.withInitialGrant(seller, at));
    wallet.creditSettlement(9001, at);
    entries.saveAndFlush(
        WalletEntry.sellerPayout(seller, payment.id(), 9001, wallet.getBalance(), at));
    assertThat(wallet.getBalance()).isEqualTo(1009001);
    assertThatThrownBy(() ->
            entries.saveAndFlush(
                WalletEntry.sellerPayout(seller, payment.id(), 9001, wallet.getBalance(), at)))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }
}
