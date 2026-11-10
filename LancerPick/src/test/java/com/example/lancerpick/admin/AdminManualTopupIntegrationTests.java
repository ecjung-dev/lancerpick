package com.example.lancerpick.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.lancerpick.admin.service.AdminReprocessingService;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.wallet.gateway.TossPaymentGateway;
import com.example.lancerpick.wallet.gateway.TossPaymentGateway.Payment;
import com.example.lancerpick.wallet.service.TopupApprovalTransactions;
import com.example.lancerpick.wallet.service.TopupRecoveryJobService;
import com.example.lancerpick.wallet.service.WalletInitializationService;
import com.example.lancerpick.wallet.service.WalletTopupService;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 자동 복구/Batch를 켜지 않아도 감사된 관리자 단건 조회가 실제 지급 경계를 사용할 수 있다. */
@IntegrationTest
@SpringBootTest(properties = "lancerpick.wallet.topup-recovery.enabled=false")
class AdminManualTopupIntegrationTests {
  @Autowired AdminReprocessingService service;
  @Autowired MemberRepository members;
  @Autowired WalletInitializationService wallets;
  @Autowired WalletTopupService topups;
  @Autowired TopupApprovalTransactions approvals;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @Autowired ApplicationContext context;
  @MockitoBean TossPaymentGateway gateway;
  @MockitoBean Clock clock;

  @Test
  void manualRecheckWorksWithAutomaticRecoveryAndJobBeansAbsent() {
    var at = Instant.parse("2026-11-10T00:00:00Z");
    when(clock.instant()).thenReturn(at);
    var tx = new TransactionTemplate(manager);
    long admin =
        members
            .saveAndFlush(
                Member.create(
                    UUID.randomUUID() + "@ops.test",
                    "fixture",
                    "운영자",
                    LocalDateTime.ofInstant(at, ZoneOffset.UTC)))
            .getId();
    long buyer =
        members
            .saveAndFlush(
                Member.create(
                    UUID.randomUUID() + "@ops.test",
                    "fixture",
                    "회원",
                    LocalDateTime.ofInstant(at, ZoneOffset.UTC)))
            .getId();
    try {
      tx.executeWithoutResult(ignored -> wallets.initializeForNewMember(buyer));
      jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", admin);
      var response = topups.create(buyer, 10000);
      long id = Long.parseLong(response.id());
      String key = "test-lookup-" + id;
      approvals.prepare(buyer, id, key, response.tossOrderId(), 10000);
      when(clock.instant()).thenReturn(at.plusSeconds(121));
      when(gateway.lookup(key))
          .thenReturn(
              new Payment(
                  key,
                  response.tossOrderId(),
                  10000,
                  10000,
                  "KRW",
                  "DONE",
                  OffsetDateTime.ofInstant(at.plusSeconds(1), ZoneOffset.UTC)));
      assertThat(context.getBeansOfType(TopupRecoveryJobService.class)).isEmpty();
      assertThat(
              service
                  .recheckTopup(admin, id, "자동 복구 없이 확인", UUID.randomUUID())
                  .receipt()
                  .resultCode())
          .isEqualTo("TOPUP_CREDITED");
      assertThat(
              jdbc.queryForObject(
                  "SELECT balance FROM wallets WHERE member_id=?", Long.class, buyer))
          .isEqualTo(1_010_000);
    } finally {
      jdbc.update("DELETE FROM admin_action_logs WHERE admin_id=?", admin);
      jdbc.update("DELETE FROM wallet_entries WHERE member_id=?", buyer);
      jdbc.update("DELETE FROM wallet_topups WHERE member_id=?", buyer);
      jdbc.update("DELETE FROM wallets WHERE member_id=?", buyer);
      jdbc.update("DELETE FROM members WHERE id IN (?,?)", admin, buyer);
    }
  }
}
