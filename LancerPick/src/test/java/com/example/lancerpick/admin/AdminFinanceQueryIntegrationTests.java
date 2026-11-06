package com.example.lancerpick.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason;
import com.example.lancerpick.admin.repository.AdminFinanceQueries.PaymentFilter;
import com.example.lancerpick.admin.repository.AdminFinanceQueries.TopupFilter;
import com.example.lancerpick.admin.service.AdminFinanceQueryService;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.member.service.MemberService;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderClosure;
import com.example.lancerpick.order.repository.OrderClosureRepository;
import com.example.lancerpick.order.repository.OrderRepository;
import com.example.lancerpick.payment.entity.OrderFunds;
import com.example.lancerpick.payment.entity.Payment;
import com.example.lancerpick.payment.repository.OrderFundsRepository;
import com.example.lancerpick.payment.repository.PaymentRepository;
import com.example.lancerpick.settlement.entity.Settlement;
import com.example.lancerpick.settlement.repository.SettlementRepository;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.support.TestMembers;
import com.example.lancerpick.wallet.config.TopupRecoveryBatchConfiguration;
import com.example.lancerpick.wallet.entity.WalletTopupStatus;
import com.example.lancerpick.wallet.gateway.TossPaymentGateway;
import com.example.lancerpick.wallet.repository.WalletTopupRepository;
import com.example.lancerpick.wallet.service.TopupApprovalTransactions;
import com.example.lancerpick.wallet.service.WalletTopupService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** 실제 DB 읽기 경계. 금융 상태 조합은 fixture이며 거래 명령 자체의 검증과 구분한다. */
@IntegrationTest
@SpringBootTest(properties = "lancerpick.wallet.topup-recovery.enabled=true")
class AdminFinanceQueryIntegrationTests {
  private static final Instant BASE = Instant.parse("2026-11-06T00:00:00Z");
  @Autowired AdminFinanceQueryService service;
  @Autowired MemberService members;
  @Autowired WalletTopupService topups;
  @Autowired TopupApprovalTransactions approvals;
  @Autowired WalletTopupRepository topupRepository;
  @Autowired OrderRepository orders;
  @Autowired PaymentRepository payments;
  @Autowired OrderFundsRepository funds;
  @Autowired SettlementRepository settlements;
  @Autowired OrderClosureRepository closures;
  @Autowired JobRepository jobs;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @Autowired JsonMapper mapper;
  @Autowired TestClock clock;
  @MockitoBean TossPaymentGateway gateway;
  long admin, buyer, seller, category, product;
  final List<Long> memberIds = new ArrayList<>();
  final List<String> emails = new ArrayList<>();
  final List<Long> orderIds = new ArrayList<>();
  final List<Long> instanceIds = new ArrayList<>();

  @TestConfiguration
  static class Time {
    @Bean
    @Primary
    TestClock adminQueryClock() {
      return new TestClock();
    }
  }

  static class TestClock extends Clock {
    Instant now = BASE;

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId ignored) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }

  @BeforeEach
  void fixture() {
    clock.now = BASE;
    admin = member();
    buyer = member();
    seller = member();
    jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", admin);
  }

  @AfterEach
  void cleanup() {
    for (long id : orderIds) {
      for (String table :
          List.of("order_closures", "settlements", "order_funds", "payments", "orders")) {
        jdbc.update(
            "DELETE FROM "
                + table
                + " WHERE "
                + (table.equals("orders") ? "id" : "order_id")
                + "=?",
            id);
      }
    }
    if (product != 0) jdbc.update("DELETE FROM products WHERE id=?", product);
    if (category != 0) jdbc.update("DELETE FROM categories WHERE id=?", category);
    for (String email : emails) TestMembers.deleteSignupProofs(jdbc, email);
    for (long id : memberIds) {
      jdbc.update("DELETE FROM wallet_entries WHERE member_id=?", id);
      jdbc.update("DELETE FROM wallet_topups WHERE member_id=?", id);
      jdbc.update("DELETE FROM wallets WHERE member_id=?", id);
      jdbc.update("DELETE FROM members WHERE id=?", id);
    }
    for (long id : instanceIds) jobs.deleteJobInstance(jobs.getJobInstance(id));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 4, 5})
  void allEntrypointsDenyOrdinaryMembersBeforeLookingUpTargets(int entry) {
    Runnable action =
        switch (entry) {
          case 0 -> () -> service.topups(buyer, null, 0, 20);
          case 1 -> () -> service.topup(buyer, Long.MAX_VALUE);
          case 2 -> () -> service.payments(buyer, null, 0, 20);
          case 3 -> () -> service.orderFinance(buyer, Long.MAX_VALUE);
          case 4 -> () -> service.recoveryJobs(buyer, 0, 20);
          default -> () -> service.recoveryJob(buyer, Long.MAX_VALUE);
        };
    fails(action, HttpStatus.FORBIDDEN);
  }

  @Test
  void currentDatabaseRoleIsUsedAndMissingMembersNeverReadFinance() {
    assertThat(service.topups(admin, null, 0, 20).totalElements()).isZero();
    jdbc.update("UPDATE members SET role='USER' WHERE id=?", admin);
    fails(() -> service.topups(admin, null, 0, 20), HttpStatus.FORBIDDEN);
    fails(() -> service.topups(0, null, 0, 20), HttpStatus.UNAUTHORIZED);
    fails(() -> service.topups(Long.MAX_VALUE, null, 0, 20), HttpStatus.UNAUTHORIZED);
  }

  @Test
  void topupPagingFiltersTieBreakByIdAndReturnNoCredentials() {
    long first = pending(buyer);
    long second = pending(buyer);
    pending(seller);
    var filter = new TopupFilter(buyer, WalletTopupStatus.PROCESSING);
    var page = service.topups(admin, filter, 0, 1);
    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.totalPages()).isEqualTo(2);
    assertThat(page.items()).extracting(v -> v.id()).containsExactly(Long.toString(second));
    assertThat(service.topups(admin, filter, 1, 1).items())
        .extracting(v -> v.id())
        .containsExactly(Long.toString(first));
    assertThat(service.topups(admin, filter, Integer.MAX_VALUE, 100).items()).isEmpty();
    var json = mapper.writeValueAsString(page);
    assertThat(json)
        .doesNotContain(
            "sensitive-payment-key", "paymentKey", "approvalRequestKey", "password", "leaseToken");
  }

  @Test
  void inspectionDoesNotClaimOrResetAttemptsAndSeparatesAutomaticLimitFromManualCandidate() {
    long id = pending(buyer);
    assertThat(service.topup(admin, id).manualRecheck().reason()).isEqualTo(BlockReason.TOO_EARLY);
    clock.now = BASE.plusSeconds(120);
    var ready = service.topup(admin, id);
    assertThat(ready.automaticRecovery().eligible()).isTrue();
    assertThat(ready.manualRecheck().eligible()).isTrue();
    jdbc.update("UPDATE wallet_topups SET recovery_attempt_count=8 WHERE id=?", id);
    var capped = service.topup(admin, id);
    assertThat(capped.automaticRecovery().reason()).isEqualTo(BlockReason.AUTOMATIC_LIMIT_REACHED);
    assertThat(capped.manualRecheck().eligible()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT recovery_attempt_count FROM wallet_topups WHERE id=?", Integer.class, id))
        .isEqualTo(8);
    assertThat(
            jdbc.queryForObject(
                "SELECT recovery_lease_token FROM wallet_topups WHERE id=?", String.class, id))
        .isNull();
    verify(gateway, never()).lookup(anyString());
  }

  @Test
  void activeLeaseBackoffAndTerminalStatesBlockManualCandidateWithoutReleasingThem() {
    long id = pending(buyer);
    clock.now = BASE.plusSeconds(121);
    jdbc.update(
        "UPDATE wallet_topups SET recovery_lease_token=?,recovery_lease_until=? WHERE id=?",
        UUID.randomUUID().toString(),
        at(BASE.plusSeconds(200)),
        id);
    assertThat(service.topup(admin, id).manualRecheck().reason())
        .isEqualTo(BlockReason.LEASE_ACTIVE);
    clock.now = BASE.plusSeconds(200);
    assertThat(service.topup(admin, id).manualRecheck().eligible()).isTrue();
    jdbc.update(
        "UPDATE wallet_topups SET next_recovery_at=? WHERE id=?", at(BASE.plusSeconds(300)), id);
    assertThat(service.topup(admin, id).manualRecheck().reason())
        .isEqualTo(BlockReason.RETRY_DELAY);
    new TransactionTemplate(manager)
        .executeWithoutResult(ignored -> {
          topupRepository.findByIdForUpdate(id).orElseThrow().markFailed(clock.instant());
        });
    assertThat(service.topup(admin, id).manualRecheck().reason())
        .isEqualTo(BlockReason.TERMINAL_STATE);
  }

  @Test
  void missingProviderConfigurationAndUnknownErrorTextsAreSafeReadResults() {
    long id = pending(buyer);
    clock.now = BASE.plusSeconds(121);
    jdbc.update(
        "UPDATE wallet_topups SET last_error_code='sensitive-provider-response' WHERE id=?", id);
    doThrow(new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "TOPUP_DISABLED", "disabled"))
        .when(gateway)
        .requireAvailable();
    var result = service.topup(admin, id);
    assertThat(result.manualRecheck().reason()).isEqualTo(BlockReason.PROVIDER_DISABLED);
    assertThat(result.lastErrorCode()).isEqualTo("RESULT_REQUIRES_REVIEW");
    assertThat(mapper.writeValueAsString(result)).doesNotContain("sensitive-provider-response");
  }

  @Test
  void unpaidAndLegacyOrdersKeepMissingRecordsDistinctFromZero() {
    long unpaid = order(TradePolicyVersion.CONTRACT_V3, "UNPAID");
    var detail = service.orderFinance(admin, unpaid);
    assertThat(detail.orderAmount()).isEqualTo(10001);
    assertThat(detail.payment()).isNull();
    assertThat(detail.funds()).isNull();
    assertThat(detail.settlement()).isNull();
    long legacy = order(TradePolicyVersion.LEGACY_V1, "PAID");
    var old = service.orderFinance(admin, legacy);
    assertThat(old.payment().policyVersion()).isEqualTo("LEGACY_V1");
    assertThat(old.funds()).isNull();
    assertThat(old.closure()).isNull();
  }

  @Test
  void paymentFiltersAndLinkedCompletedOrRefundedAmountsAreReadWithoutMutation() {
    long paid = order(TradePolicyVersion.CONTRACT_V3, "COMPLETED");
    long refunded = order(TradePolicyVersion.CONTRACT_V3, "REFUNDED");
    var detail = service.orderFinance(admin, paid);
    assertThat(detail.funds().status()).isEqualTo("RELEASED");
    assertThat(detail.funds().remainingAmount()).isZero();
    assertThat(detail.settlement().feeAmount()).isEqualTo(1000);
    assertThat(detail.settlement().sellerAmount()).isEqualTo(9001);
    assertThat(detail.closure().sellerNetAmount()).isEqualTo(9001);
    var selected =
        service.payments(
            admin,
            new PaymentFilter(null, buyer, seller, "REFUNDED", TradePolicyVersion.CONTRACT_V3),
            0,
            20);
    assertThat(selected.items())
        .singleElement()
        .satisfies(v -> {
          assertThat(v.orderId()).isEqualTo(Long.toString(refunded));
          assertThat(v.refundedAmount()).isEqualTo(10001);
          assertThat(v.refundedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        });
    assertThat(service.orderFinance(admin, refunded).funds().status()).isEqualTo("REFUNDED");
    assertThat(
            service
                .payments(admin, new PaymentFilter(paid, null, null, null, null), 0, 20)
                .totalElements())
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject("SELECT balance FROM wallets WHERE member_id=?", Long.class, buyer))
        .isEqualTo(1000000);
  }

  @Test
  void batchReadsAreScopedAndNeverExposeParametersContextsOrFailureMessages() {
    var execution = job(TopupRecoveryBatchConfiguration.JOB_NAME, BatchStatus.FAILED);
    execution.setExitStatus(
        new org.springframework.batch.core.ExitStatus("FAILED", "sensitive-exception-body"));
    jobs.update(execution);
    var step = jobs.createStepExecution("walletTopupRecoveryStep", execution);
    step.setReadCount(3);
    step.setWriteCount(2);
    step.setFilterCount(1);
    step.getExecutionContext().putLong("completed", 2);
    step.getExecutionContext().putString("paymentKey", "sensitive-metadata-key");
    jobs.update(step);
    jobs.updateExecutionContext(step);
    var result = service.recoveryJob(admin, execution.getId());
    assertThat(result.restart().eligible()).isTrue();
    assertThat(result.scanRequestedAt().toInstant()).isEqualTo(BASE);
    assertThat(result.creditedCount()).isEqualTo(2);
    assertThat(result.outcomes().get("completed")).isEqualTo(2);
    assertThat(service.recoveryJobs(admin, 0, 20).items())
        .singleElement()
        .satisfies(v -> assertThat(v.creditedCount()).isEqualTo(2));
    assertThat(mapper.writeValueAsString(result))
        .doesNotContain("sensitive-", "paymentKey", "run.id", "requestedAtMillis");
    var foreign = job("unrelatedJob", BatchStatus.FAILED);
    fails(() -> service.recoveryJob(admin, foreign.getId()), HttpStatus.NOT_FOUND);
    assertThat(service.recoveryJobs(admin, 0, 20).items())
        .extracting(v -> v.executionId())
        .containsExactly(Long.toString(execution.getId()));
  }

  @Test
  void runningCompletedAndOldExecutionsCannotBeOfferedAsRestartCandidates() {
    var older = job(TopupRecoveryBatchConfiguration.JOB_NAME, BatchStatus.FAILED);
    var newer =
        jobs.createJobExecution(
            older.getJobInstance(), older.getJobParameters(), new ExecutionContext());
    newer.setStatus(BatchStatus.STARTED);
    jobs.update(newer);
    assertThat(service.recoveryJob(admin, older.getId()).restart().reason())
        .isEqualTo(BlockReason.NEWER_EXECUTION_EXISTS);
    assertThat(service.recoveryJob(admin, newer.getId()).restart().reason())
        .isEqualTo(BlockReason.JOB_RUNNING);
    newer.setStatus(BatchStatus.COMPLETED);
    jobs.update(newer);
    assertThat(service.recoveryJob(admin, newer.getId()).restart().reason())
        .isEqualTo(BlockReason.JOB_NOT_RESTARTABLE);
    assertThat(service.recoveryJobs(admin, 0, 20).items())
        .extracting(v -> v.executionId())
        .containsExactly(Long.toString(newer.getId()));
  }

  @Test
  void instanceWithoutExecutionIsVisibleAndNotRestartable() {
    var parameters =
        new JobParametersBuilder()
            .addString("run.id", UUID.randomUUID().toString())
            .addLong("requestedAtMillis", BASE.toEpochMilli())
            .toJobParameters();
    var instance = jobs.createJobInstance(TopupRecoveryBatchConfiguration.JOB_NAME, parameters);
    instanceIds.add(instance.getInstanceId());
    assertThat(service.recoveryJobs(admin, 0, 20).items())
        .singleElement()
        .satisfies(v -> {
          assertThat(v.executionId()).isNull();
          assertThat(v.status()).isEqualTo("NO_EXECUTION");
          assertThat(v.restart().eligible()).isFalse();
        });
  }

  @Test
  void unexpectedJobParametersAreNotEchoedOrOfferedForRestart() {
    var parameters =
        new JobParametersBuilder()
            .addString("paymentKey", "sensitive-job-parameter")
            .toJobParameters();
    var instance = jobs.createJobInstance(TopupRecoveryBatchConfiguration.JOB_NAME, parameters);
    instanceIds.add(instance.getInstanceId());
    var execution = jobs.createJobExecution(instance, parameters, new ExecutionContext());
    execution.setStatus(BatchStatus.FAILED);
    jobs.update(execution);
    var result = service.recoveryJob(admin, execution.getId());
    assertThat(result.restart().reason()).isEqualTo(BlockReason.INVALID_JOB_PARAMETERS);
    assertThat(mapper.writeValueAsString(result))
        .doesNotContain("sensitive-job-parameter", "paymentKey");
  }

  @Test
  void invalidPaginationFiltersAndMissingTargetsAreRejected() {
    fails(() -> service.topups(admin, null, -1, 20), HttpStatus.BAD_REQUEST);
    fails(() -> service.topups(admin, null, 0, 101), HttpStatus.BAD_REQUEST);
    fails(() -> service.topups(admin, new TopupFilter(-1L, null), 0, 20), HttpStatus.BAD_REQUEST);
    fails(
        () -> service.payments(admin, new PaymentFilter(null, null, null, "paid", null), 0, 20),
        HttpStatus.BAD_REQUEST);
    fails(() -> service.recoveryJobs(admin, Integer.MAX_VALUE, 100), HttpStatus.BAD_REQUEST);
    fails(() -> service.orderFinance(admin, Long.MAX_VALUE), HttpStatus.NOT_FOUND);
    fails(() -> service.topup(admin, Long.MAX_VALUE), HttpStatus.NOT_FOUND);
  }

  long member() {
    String email = "ops-" + UUID.randomUUID() + "@example.test";
    emails.add(email);
    long id =
        Long.parseLong(
            members.signup(TestMembers.signupRequest(jdbc, email, "abcd1234", "운영 조회")).id());
    memberIds.add(id);
    return id;
  }

  long pending(long member) {
    var topup = topups.create(member, 50000);
    long id = Long.parseLong(topup.id());
    approvals.prepare(member, id, "sensitive-payment-key-" + id, topup.tossOrderId(), 50000);
    return id;
  }

  long order(TradePolicyVersion policy, String state) {
    if (product == 0) {
      String name = "ops-" + UUID.randomUUID();
      jdbc.update("INSERT INTO categories(name,display_order) VALUES (?,0)", name);
      category = jdbc.queryForObject("SELECT id FROM categories WHERE name=?", Long.class, name);
      jdbc.update(
          "INSERT INTO"
              + " products(seller_id,category_id,title,description,scope_description,preparation_description,price,duration_days,created_at,updated_at)"
              + " VALUES (?,?,'조회 상품','설명','범위','준비',10001,1,?,?)",
          seller,
          category,
          at(BASE),
          at(BASE));
      product =
          jdbc.queryForObject("SELECT id FROM products WHERE seller_id=?", Long.class, seller);
    }
    return new TransactionTemplate(manager)
        .execute(ignored -> {
          var at = at(BASE);
          var order =
              Order.create(buyer, seller, product, UUID.randomUUID(), 10001L, 1, at, policy);
          order.accept(at);
          if (policy != TradePolicyVersion.LEGACY_V1) order.contractSigned(at);
          orders.saveAndFlush(order);
          orderIds.add(order.getId());
          if (state.equals("UNPAID")) return order.getId();
          order.pay(at);
          var payment =
              payments.saveAndFlush(
                  policy == TradePolicyVersion.LEGACY_V1
                      ? Payment.create(order.getId(), 10001, at)
                      : Payment.fullFunding(order.getId(), 10001, at));
          OrderFunds held =
              policy == TradePolicyVersion.CONTRACT_V3
                  ? funds.saveAndFlush(OrderFunds.hold(order.getId(), payment.id(), 10001, at))
                  : null;
          if (state.equals("COMPLETED")) {
            order.deliver(at);
            order.confirm(at);
            held.release(at);
            settlements.saveAndFlush(Settlement.create(order.getId(), 10001, 1000, at));
            closures.saveAndFlush(OrderClosure.completed(order.getId(), buyer, 10001, at));
          } else if (state.equals("REFUNDED")) {
            order.cancel(at);
            payment.refund(at);
            held.refund(at);
          }
          return order.getId();
        });
  }

  JobExecution job(String name, BatchStatus status) {
    var parameters =
        new JobParametersBuilder()
            .addString("run.id", UUID.randomUUID().toString())
            .addLong("requestedAtMillis", BASE.toEpochMilli())
            .toJobParameters();
    var instance = jobs.createJobInstance(name, parameters);
    instanceIds.add(instance.getInstanceId());
    var execution = jobs.createJobExecution(instance, parameters, new ExecutionContext());
    execution.setStatus(status);
    jobs.update(execution);
    return execution;
  }

  static LocalDateTime at(Instant time) {
    return LocalDateTime.ofInstant(time, ZoneOffset.UTC);
  }

  static void fails(Runnable action, HttpStatus status) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class, failure -> assertThat(failure.status()).isEqualTo(status));
  }
}
