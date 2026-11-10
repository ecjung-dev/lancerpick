package com.example.lancerpick.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.wallet.batch.TopupRecoveryJobParameters;
import com.example.lancerpick.wallet.config.TopupRecoveryBatchConfiguration;
import com.example.lancerpick.wallet.dto.WalletTopupResponse;
import com.example.lancerpick.wallet.entity.WalletTopupStatus;
import com.example.lancerpick.wallet.gateway.TossGatewayException;
import com.example.lancerpick.wallet.gateway.TossPaymentGateway;
import com.example.lancerpick.wallet.gateway.TossPaymentGateway.Payment;
import com.example.lancerpick.wallet.repository.TopupRecoveryQueries;
import com.example.lancerpick.wallet.repository.WalletEntryRepository;
import com.example.lancerpick.wallet.service.TopupApprovalTransactions;
import com.example.lancerpick.wallet.service.TopupRecoveryJobService;
import com.example.lancerpick.wallet.service.TopupRecoveryTransactions;
import com.example.lancerpick.wallet.service.TopupRecoveryTransactions.Result;
import com.example.lancerpick.wallet.service.WalletInitializationService;
import com.example.lancerpick.wallet.service.WalletTopupApprovalService;
import com.example.lancerpick.wallet.service.WalletTopupService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 Batch JDBC 저장소·MySQL 잠금/롤백/체크포인트. 외부 제공자만 대역으로 바꾼다. */
@IntegrationTest
@SpringBootTest(
    properties = {
      "lancerpick.wallet.topup-recovery.enabled=true",
      "lancerpick.wallet.topup-recovery.scheduling-enabled=false",
      "lancerpick.wallet.topup-recovery.max-attempts=3"
    })
class TopupRecoveryIntegrationTests {
  private static final AtomicLong MEMBER_IDS = new AtomicLong(4_800_000);
  private static final Instant BASE = Instant.parse("2026-11-06T00:00:00Z");
  private final List<Long> owners = new ArrayList<>();
  @Autowired JdbcTemplate jdbc;
  @Autowired WalletInitializationService initialization;
  @Autowired WalletTopupService topups;
  @Autowired WalletTopupApprovalService manual;
  @Autowired TopupApprovalTransactions approvals;
  @Autowired TopupRecoveryTransactions recovery;
  @Autowired TopupRecoveryJobService jobs;
  @Autowired JobRepository jobRepository;
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired MutableClock clock;
  @MockitoBean TossPaymentGateway gateway;
  @MockitoSpyBean WalletEntryRepository entries;
  @MockitoSpyBean TopupRecoveryQueries queries;

  @TestConfiguration
  static class TimeConfiguration {
    @Bean
    @Primary
    MutableClock recoveryTestClock() {
      return new MutableClock();
    }
  }

  static class MutableClock extends Clock {
    volatile Instant now = BASE;

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }

    void advance(long seconds) {
      now = now.plusSeconds(seconds);
    }
  }

  record Pending(long owner, WalletTopupResponse topup, String paymentKey) {
    long id() {
      return Long.parseLong(topup.id());
    }
  }

  @BeforeEach
  void resetClock() {
    clock.now = BASE;
  }

  @AfterEach
  void cleanup() {
    for (long owner : owners) {
      jdbc.update("DELETE FROM wallet_entries WHERE member_id=?", owner);
      jdbc.update("DELETE FROM wallet_topups WHERE member_id=?", owner);
      jdbc.update("DELETE FROM wallets WHERE member_id=?", owner);
      jdbc.update("DELETE FROM members WHERE id=?", owner);
    }
    var instances =
        jdbc.queryForList(
            "SELECT JOB_INSTANCE_ID FROM BATCH_JOB_INSTANCE WHERE JOB_NAME='walletTopupRecovery'",
            Long.class);
    for (long instance : instances) {
      for (long execution :
          jdbc.queryForList(
              "SELECT JOB_EXECUTION_ID FROM BATCH_JOB_EXECUTION WHERE JOB_INSTANCE_ID=?",
              Long.class,
              instance)) {
        for (long step :
            jdbc.queryForList(
                "SELECT STEP_EXECUTION_ID FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID=?",
                Long.class,
                execution))
          jdbc.update("DELETE FROM BATCH_STEP_EXECUTION_CONTEXT WHERE STEP_EXECUTION_ID=?", step);
        jdbc.update("DELETE FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID=?", execution);
        jdbc.update("DELETE FROM BATCH_JOB_EXECUTION_CONTEXT WHERE JOB_EXECUTION_ID=?", execution);
        jdbc.update("DELETE FROM BATCH_JOB_EXECUTION_PARAMS WHERE JOB_EXECUTION_ID=?", execution);
        jdbc.update("DELETE FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID=?", execution);
      }
      jdbc.update("DELETE FROM BATCH_JOB_INSTANCE WHERE JOB_INSTANCE_ID=?", instance);
    }
  }

  @Test
  void jobUsesStoredKeyOutsideTransactionAndPersistsExactlyOneCreditAndSafeMetadata()
      throws Exception {
    var pending = pending();
    clock.advance(121);
    when(gateway.lookup(pending.paymentKey()))
        .thenAnswer(call -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
          assertThat(
                  jdbc.queryForObject(
                      "SELECT recovery_lease_token FROM wallet_topups WHERE id=?",
                      String.class,
                      pending.id()))
              .isNotBlank();
          return done(pending);
        });
    JobExecution execution = completedJob();
    completedJob();
    assertCredited(pending);
    verify(gateway, times(1)).lookup(pending.paymentKey());
    verify(gateway, never()).confirm(anyString(), anyString(), anyLong(), anyString());
    assertThat(
            jdbc.queryForObject(
                "SELECT STATUS FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID=?",
                String.class,
                execution.getId()))
        .isEqualTo("COMPLETED");
    var restored = jobRepository.getJobExecution(execution.getId());
    assertThat(restored.getStepExecutions())
        .singleElement()
        .satisfies(step -> {
          assertThat(step.getWriteCount()).isEqualTo(1);
          assertThat(step.getExecutionContext().getLong("lastId")).isEqualTo(pending.id());
          assertThat(step.getExecutionContext().toString())
              .doesNotContain(pending.paymentKey(), "paymentKey", "token");
        });
    assertThat(
            jdbc.queryForList(
                "SELECT PARAMETER_NAME FROM BATCH_JOB_EXECUTION_PARAMS WHERE JOB_EXECUTION_ID=?",
                String.class,
                execution.getId()))
        .containsExactlyInAnyOrder("run.id", "requestedAtMillis");
    assertThat(
            jdbc.queryForObject(
                "SELECT recovery_lease_token FROM wallet_topups WHERE id=?",
                String.class,
                pending.id()))
        .isNull();
  }

  @Test
  void readyFreshDeferredLeasedExhaustedAndTerminalRowsAreNotAutomaticallyLookedUp()
      throws Exception {
    var ready = topups.create(member(), 50000);
    var leased = pending();
    var deferred = pending();
    var exhausted = pending();
    var terminal = pending();
    approvals.apply(terminal.owner(), terminal.id(), done(terminal));
    clock.advance(121);
    recovery.claim(leased.id(), clock.instant().minusSeconds(120)).orElseThrow();
    jdbc.update(
        "UPDATE wallet_topups SET next_recovery_at=? WHERE id=?",
        utc(clock.instant().plusSeconds(600)),
        deferred.id());
    jdbc.update("UPDATE wallet_topups SET recovery_attempt_count=3 WHERE id=?", exhausted.id());
    var fresh = pending();
    completedJob();
    verify(gateway, never()).lookup(anyString());
    assertThat(topups.get(fresh.owner(), fresh.id()).status())
        .isEqualTo(WalletTopupStatus.PROCESSING);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM wallet_topups WHERE id=?", String.class, ready.id()))
        .isEqualTo("READY");
  }

  @Test
  void failedLookupBacksOffAndStopsAutomaticAttemptsButManualRecheckRemainsAvailable()
      throws Exception {
    var pending = pending();
    clock.advance(121);
    when(gateway.lookup(pending.paymentKey())).thenThrow(new TossGatewayException());
    completedJob();
    completedJob(); // next_recovery_at 이전에는 다시 조회하지 않는다.
    verify(gateway, times(1)).lookup(pending.paymentKey());
    clock.advance(61);
    completedJob();
    clock.advance(121);
    completedJob();
    clock.advance(3601);
    completedJob();
    verify(gateway, times(3)).lookup(pending.paymentKey());
    assertThat(
            jdbc.queryForObject(
                "SELECT recovery_attempt_count FROM wallet_topups WHERE id=?",
                Integer.class,
                pending.id()))
        .isEqualTo(3);
    assertThat(topups.get(pending.owner(), pending.id()).status())
        .isEqualTo(WalletTopupStatus.UNKNOWN);
    assertThat(balance(pending.owner())).isEqualTo(1_000_000);
    doReturn(done(pending)).when(gateway).lookup(pending.paymentKey());
    manual.reconcile(pending.owner(), pending.id());
    assertCredited(pending);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ABORTED", "EXPIRED", "CANCELED"})
  void terminalProviderStatesNeverGrantMoney(String status) throws Exception {
    var pending = pending();
    clock.advance(121);
    when(gateway.lookup(pending.paymentKey()))
        .thenReturn(
            new Payment(
                pending.paymentKey(),
                pending.topup().tossOrderId(),
                50000,
                0,
                "KRW",
                status,
                null));
    completedJob();
    assertThat(topups.get(pending.owner(), pending.id()).status())
        .isEqualTo(WalletTopupStatus.FAILED);
    assertThat(balance(pending.owner())).isEqualTo(1_000_000);
    assertThat(credits(pending.id())).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT recovery_lease_until FROM wallet_topups WHERE id=?",
                Object.class,
                pending.id()))
        .isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"KEY", "ORDER", "AMOUNT"})
  void changedProviderIdentityOrAmountIsDeferredWithoutCredit(String field) throws Exception {
    var pending = pending();
    clock.advance(121);
    var response =
        new Payment(
            field.equals("KEY") ? "another_key" : pending.paymentKey(),
            field.equals("ORDER") ? "another_order" : pending.topup().tossOrderId(),
            field.equals("AMOUNT") ? 1 : 50000,
            50000,
            "KRW",
            "DONE",
            OffsetDateTime.ofInstant(BASE.plusSeconds(1), ZoneOffset.UTC));
    when(gateway.lookup(pending.paymentKey())).thenReturn(response);
    completedJob();
    assertThat(balance(pending.owner())).isEqualTo(1_000_000);
    assertThat(credits(pending.id())).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT last_error_code FROM wallet_topups WHERE id=?", String.class, pending.id()))
        .isEqualTo("RESULT_REQUIRES_REVIEW");
  }

  @Test
  void oneProviderFailureDoesNotPreventLaterTopupFromRecovering() throws Exception {
    var failed = pending();
    var valid = pending();
    clock.advance(121);
    when(gateway.lookup(failed.paymentKey())).thenThrow(new TossGatewayException());
    when(gateway.lookup(valid.paymentKey())).thenReturn(done(valid));
    var execution = completedJob();
    assertThat(balance(failed.owner())).isEqualTo(1_000_000);
    assertCredited(valid);
    assertThat(execution.getStepExecutions())
        .singleElement()
        .satisfies(step -> {
          assertThat(step.getReadCount()).isEqualTo(2);
          assertThat(step.getExecutionContext().getLong("deferred")).isEqualTo(1);
        });
  }

  @Test
  void twoJobsOnlyOneWorkerClaimsTheSamePayment() throws Exception {
    var pending = pending();
    clock.advance(121);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(gateway.lookup(pending.paymentKey()))
        .thenAnswer(call -> {
          entered.countDown();
          assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
          return done(pending);
        });
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> jobs.start());
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(executor.submit(() -> jobs.start()).get(10, TimeUnit.SECONDS).getStatus())
            .isEqualTo(BatchStatus.COMPLETED);
      } finally {
        release.countDown();
      }
      assertThat(first.get(15, TimeUnit.SECONDS).getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }
    assertCredited(pending);
    verify(gateway, times(1)).lookup(pending.paymentKey());
  }

  @Test
  void manualRecheckDuringBatchLookupWinsWithoutLateWorkerOverwritingIt() throws Exception {
    var pending = pending();
    clock.advance(121);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var calls = new AtomicInteger();
    when(gateway.lookup(pending.paymentKey()))
        .thenAnswer(call -> {
          if (calls.incrementAndGet() == 1) {
            entered.countDown();
            assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
          }
          return done(pending);
        });
    try (var executor = Executors.newSingleThreadExecutor()) {
      var job = executor.submit(() -> jobs.start());
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(manual.reconcile(pending.owner(), pending.id()).status())
            .isEqualTo(WalletTopupStatus.COMPLETED);
      } finally {
        release.countDown();
      }
      assertThat(job.get(15, TimeUnit.SECONDS).getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }
    assertCredited(pending);
  }

  @Test
  void expiredOwnerCannotApplyOrDeferAfterNewWorkerTakesLease() {
    var pending = pending();
    clock.advance(121);
    var old = recovery.claim(pending.id(), clock.instant().minusSeconds(120)).orElseThrow();
    clock.advance(180);
    var current = recovery.claim(pending.id(), clock.instant().minusSeconds(120)).orElseThrow();
    assertThat(current.token()).isNotEqualTo(old.token());
    assertThat(recovery.apply(old, done(pending))).isEqualTo(Result.STALE);
    assertThat(recovery.defer(old, "PROVIDER_UNAVAILABLE")).isEqualTo(Result.STALE);
    assertThat(
            jdbc.queryForObject(
                "SELECT recovery_lease_token FROM wallet_topups WHERE id=?",
                String.class,
                pending.id()))
        .isEqualTo(current.token());
    assertThat(recovery.apply(current, done(pending))).isEqualTo(Result.COMPLETED);
    assertCredited(pending);
  }

  @Test
  void abandonedProcessingIsRecoveredAfterLeaseExpires() throws Exception {
    var pending = pending();
    clock.advance(121);
    recovery.claim(pending.id(), clock.instant().minusSeconds(120)).orElseThrow();
    clock.advance(181);
    when(gateway.lookup(pending.paymentKey())).thenReturn(done(pending));
    completedJob();
    assertCredited(pending);
    assertThat(
            jdbc.queryForObject(
                "SELECT recovery_attempt_count FROM wallet_topups WHERE id=?",
                Integer.class,
                pending.id()))
        .isEqualTo(2);
  }

  @Test
  void databaseFailureRollsBackMoneyAndLedgerThenCanRecoverLater() throws Exception {
    var pending = pending();
    clock.advance(121);
    when(gateway.lookup(pending.paymentKey())).thenReturn(done(pending));
    AtomicBoolean failOnce = new AtomicBoolean(true);
    doAnswer(
            call -> {
              if (failOnce.getAndSet(false))
                jdbc.update(
                    "INSERT INTO"
                        + " wallet_entries(member_id,topup_id,type,amount_delta,balance_after,event_key,created_at)"
                        + " VALUES (?,?,'TOPUP',0,0,?,?)",
                    pending.owner(),
                    pending.id(),
                    "invalid-recovery:" + pending.id(),
                    utc(clock.instant()));
              return call.callRealMethod();
            })
        .when(entries)
        .insertTopup(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), any());
    completedJob();
    assertThat(balance(pending.owner())).isEqualTo(1_000_000);
    assertThat(credits(pending.id())).isZero();
    assertThat(topups.get(pending.owner(), pending.id()).status())
        .isEqualTo(WalletTopupStatus.UNKNOWN);
    clock.advance(61);
    completedJob();
    assertCredited(pending);
  }

  @Test
  void failedJobRestartsFromPersistedCheckpointAndNeverStoresRawFailure() throws Exception {
    var first = pending();
    var second = pending();
    clock.advance(121);
    when(gateway.lookup(first.paymentKey())).thenReturn(done(first));
    when(gateway.lookup(second.paymentKey())).thenReturn(done(second));
    AtomicBoolean failOnce = new AtomicBoolean(true);
    doAnswer(
            call -> {
              long afterId = call.getArgument(0);
              if (afterId >= first.id() && failOnce.getAndSet(false))
                throw new IllegalStateException("sensitive-provider-payload");
              return call.callRealMethod();
            })
        .when(queries)
        .nextId(anyLong(), anyLong(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    var failed = jobs.start();
    assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertCredited(first);
    assertThat(credits(second.id())).isZero();
    assertThat(failed.getAllFailureExceptions())
        .allSatisfy(
            error -> assertThat(error.toString()).doesNotContain("sensitive-provider-payload"));
    var restarted = jobs.restart(failed.getId());
    assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(restarted.getJobInstanceId()).isEqualTo(failed.getJobInstanceId());
    assertCredited(first);
    assertCredited(second);
    verify(gateway, times(1)).lookup(first.paymentKey());
    verify(gateway, times(1)).lookup(second.paymentKey());
  }

  @Test
  void restartRevalidatesStoredParametersWithTheSharedContract() throws Exception {
    // 정상 파라미터로 저장된 중단 실행은 재시작되고, 계약 범위(UTC 9999년)를 넘는 저장 파라미터는 재시작 때 검증기가 막는다.
    // JobOperator.restart는 검증 실패를 JobRestartException으로 감싼다(관리자 재시작은 BATCH_RESTART_REJECTED로 기록).
    var stopped = storedExecution(Instant.parse("2026-11-10T00:00:00Z").toEpochMilli(), BatchStatus.STOPPED);
    var restarted = jobs.restart(stopped.getId());
    assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(restarted.getJobInstanceId()).isEqualTo(stopped.getJobInstanceId());

    var outOfRange =
        storedExecution(Instant.parse("+10000-01-01T00:00:00Z").toEpochMilli(), BatchStatus.FAILED);
    assertThatThrownBy(() -> jobs.restart(outOfRange.getId()))
        .isInstanceOf(JobRestartException.class)
        .hasMessage("Only a server run ID and start time are permitted");
    verify(gateway, never()).lookup(anyString());
  }

  private JobExecution storedExecution(long requestedAtMillis, BatchStatus status) {
    var parameters =
        new JobParametersBuilder()
            .addString(TopupRecoveryJobParameters.RUN_ID, UUID.randomUUID().toString())
            .addLong(TopupRecoveryJobParameters.REQUESTED_AT_MILLIS, requestedAtMillis)
            .toJobParameters();
    var instance =
        jobRepository.createJobInstance(TopupRecoveryBatchConfiguration.JOB_NAME, parameters);
    var execution =
        jobRepository.createJobExecution(instance, parameters, new ExecutionContext());
    execution.setStatus(status);
    execution.setEndTime(LocalDateTime.now(ZoneOffset.UTC));
    jobRepository.update(execution);
    return execution;
  }

  private JobExecution completedJob() throws Exception {
    var execution = jobs.start();
    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    return execution;
  }

  private Pending pending() {
    long owner = member();
    var topup = topups.create(owner, 50000);
    String key = "recovery_payment_" + topup.id();
    approvals.prepare(owner, Long.parseLong(topup.id()), key, topup.tossOrderId(), topup.amount());
    return new Pending(owner, topup, key);
  }

  private long member() {
    long id = MEMBER_IDS.incrementAndGet();
    owners.add(id);
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(status -> {
          jdbc.update(
              "INSERT INTO members(id,email,password_hash,display_name,role,created_at,updated_at)"
                  + " VALUES (?,?,?,'복구 테스트','USER',?,?)",
              id,
              "recovery-" + id + "@example.test",
              "fixture-hash",
              utc(clock.instant()),
              utc(clock.instant()));
          initialization.initializeForNewMember(id);
        });
    return id;
  }

  private Payment done(Pending pending) {
    return new Payment(
        pending.paymentKey(),
        pending.topup().tossOrderId(),
        50000,
        50000,
        "KRW",
        "DONE",
        OffsetDateTime.ofInstant(BASE.plusSeconds(1), ZoneOffset.UTC));
  }

  private long balance(long owner) {
    return jdbc.queryForObject("SELECT balance FROM wallets WHERE member_id=?", Long.class, owner);
  }

  private long credits(long id) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM wallet_entries WHERE topup_id=?", Long.class, id);
  }

  private void assertCredited(Pending pending) {
    assertThat(balance(pending.owner())).isEqualTo(1_050_000);
    assertThat(credits(pending.id())).isEqualTo(1);
    assertThat(topups.get(pending.owner(), pending.id()).status())
        .isEqualTo(WalletTopupStatus.COMPLETED);
  }

  private static LocalDateTime utc(Instant instant) {
    return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
