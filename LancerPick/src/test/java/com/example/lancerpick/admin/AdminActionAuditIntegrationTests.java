package com.example.lancerpick.admin;

import static com.example.lancerpick.admin.entity.AdminAction.TOPUP_RECHECK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.lancerpick.admin.entity.AdminActionStatus;
import com.example.lancerpick.admin.service.AdminActionAuditService;
import com.example.lancerpick.admin.service.AdminActionAuditService.BeginResult;
import com.example.lancerpick.admin.service.AdminActionAuditService.Outcome;
import com.example.lancerpick.admin.service.AdminActionEligibility;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.support.IntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 MySQL에서 UNIQUE·행 잠금·독립 커밋을 검사한다. 업무 후보 판단은 별도 단위 검증한다. */
@IntegrationTest
class AdminActionAuditIntegrationTests {
  private static final Instant BASE = Instant.parse("2026-11-10T00:00:00Z");
  @Autowired AdminActionAuditService service;
  @Autowired MemberRepository members;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @MockitoBean AdminActionEligibility eligibility;
  @MockitoBean Clock clock;
  private final List<Long> memberIds = new ArrayList<>();
  long admin, otherAdmin, user;

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(BASE);
    admin = member(true);
    otherAdmin = member(true);
    user = member(false);
    when(eligibility.requireEligible(anyLong(), any(), anyLong())).thenReturn(null);
  }

  @AfterEach
  void cleanup() {
    for (long id : memberIds) {
      jdbc.update("DELETE FROM admin_action_logs WHERE admin_id=?", id);
      jdbc.update("DELETE FROM members WHERE id=?", id);
    }
  }

  @Test
  void requestIsCommittedBeforeWorkAndReplayNeverExecutesAgain() {
    UUID key = UUID.randomUUID();
    var first = begin(admin, 123, "  처리 확인  ", key);
    assertThat(first.execute()).isTrue();
    assertThat(first.receipt().reason()).isEqualTo("처리 확인");
    assertThat(first.receipt().status()).isEqualTo(AdminActionStatus.REQUESTED);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM admin_action_logs WHERE id=?",
                Long.class,
                Long.parseLong(first.receipt().id())))
        .isEqualTo(1L);
    var replay = begin(admin, 123, "처리 확인", key);
    assertThat(replay.execute()).isFalse();
    assertThat(replay.receipt()).isEqualTo(first.receipt());
  }

  @Test
  void sameKeyCannotBeReusedForDifferentReasonOrTarget() {
    UUID key = UUID.randomUUID();
    begin(admin, 123, "확인", key);
    fails(() -> begin(admin, 124, "확인", key), "ADMIN_ACTION_REQUEST_CONFLICT");
    fails(() -> begin(admin, 123, "다른 사유", key), "ADMIN_ACTION_REQUEST_CONFLICT");
  }

  @Test
  void differentAdministratorsCannotReserveSameTargetConcurrently() throws Exception {
    var results = concurrent(false);
    assertThat(results.stream().filter(BeginResult.class::isInstance).count()).isEqualTo(1);
    assertThat(results.stream().filter("ADMIN_ACTION_IN_PROGRESS"::equals).count()).isEqualTo(1);
  }

  @Test
  void concurrentSameKeyReturnsOneExecutionAndOneReplay() throws Exception {
    var results = concurrent(true);
    assertThat(results).allMatch(BeginResult.class::isInstance);
    assertThat(results.stream().map(BeginResult.class::cast).filter(BeginResult::execute).count())
        .isEqualTo(1);
    assertThat(
            results.stream()
                .map(BeginResult.class::cast)
                .map(r -> r.receipt().id())
                .distinct()
                .count())
        .isEqualTo(1);
  }

  @Test
  void expiredRequestBecomesUnknownAndLateCompletionCannotOverwriteIt() {
    var first = begin(admin, 123, "확인", UUID.randomUUID());
    when(clock.instant()).thenReturn(BASE.plusSeconds(600));
    var next = begin(otherAdmin, 123, "다시 확인", UUID.randomUUID());
    assertThat(next.execute()).isTrue();
    var old = service.inspect(admin, Long.parseLong(first.receipt().id()));
    assertThat(old.status()).isEqualTo(AdminActionStatus.UNKNOWN);
    assertThat(old.resultCode()).isEqualTo("LEASE_EXPIRED");
    assertThat(service.complete(Long.parseLong(old.id()), Outcome.SUCCEEDED)).isEqualTo(old);
  }

  @Test
  void activeRequestIsNotReplacedAndReplayDoesNotExtendTimeout() {
    var key = UUID.randomUUID();
    var first = begin(admin, 123, "확인", key);
    when(clock.instant()).thenReturn(BASE.plusSeconds(599));
    fails(() -> begin(otherAdmin, 123, "확인", UUID.randomUUID()), "ADMIN_ACTION_IN_PROGRESS");
    assertThat(begin(admin, 123, "확인", key).receipt().leaseUntil())
        .isEqualTo(first.receipt().leaseUntil());
  }

  @Test
  void terminalOutcomeIsFirstWriterWinsAndFailedWorkKeepsRequest() {
    var first = begin(admin, 123, "확인", UUID.randomUUID());
    long id = Long.parseLong(first.receipt().id());
    var failed = service.complete(id, Outcome.EXECUTION_FAILED);
    assertThat(failed.status()).isEqualTo(AdminActionStatus.FAILED);
    assertThat(service.complete(id, Outcome.SUCCEEDED)).isEqualTo(failed);
    assertThat(begin(admin, 123, "새 실행", UUID.randomUUID()).execute()).isTrue();
  }

  @Test
  void unknownOutcomeIsNotReportedAsFailureOrSuccess() {
    var first = begin(admin, 123, "확인", UUID.randomUUID());
    assertThat(
            service
                .complete(Long.parseLong(first.receipt().id()), Outcome.RESULT_UNCONFIRMED)
                .status())
        .isEqualTo(AdminActionStatus.UNKNOWN);
  }

  @Test
  void candidateFailureRollsBackAuditInsertion() {
    when(eligibility.requireEligible(admin, TOPUP_RECHECK, 123))
        .thenThrow(
            new BusinessException(HttpStatus.CONFLICT, "ADMIN_ACTION_NOT_ELIGIBLE", "대상 아님"));
    fails(() -> begin(admin, 123, "확인", UUID.randomUUID()), "ADMIN_ACTION_NOT_ELIGIBLE");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM admin_action_logs WHERE admin_id=?", Long.class, admin))
        .isZero();
  }

  @Test
  void currentDatabaseRoleIsCheckedEvenOnReplay() {
    var key = UUID.randomUUID();
    var log = begin(admin, 123, "확인", key);
    jdbc.update("UPDATE members SET role='USER' WHERE id=?", admin);
    fails(() -> begin(admin, 123, "확인", key), "FORBIDDEN");
    fails(() -> service.inspect(user, Long.parseLong(log.receipt().id())), "FORBIDDEN");
  }

  @Test
  void unauthorizedOrInvalidRequestsNeverReachTargetLookup() {
    fails(() -> begin(user, 123, "확인", UUID.randomUUID()), "FORBIDDEN");
    fails(() -> begin(0, 123, "확인", UUID.randomUUID()), "AUTHENTICATION_REQUIRED");
    fails(() -> begin(admin, 123, " ", UUID.randomUUID()), "INVALID_ADMIN_ACTION");
    fails(() -> begin(admin, 0, "확인", UUID.randomUUID()), "INVALID_ADMIN_ACTION");
    fails(() -> begin(admin, 123, "확인", null), "INVALID_ADMIN_ACTION");
    fails(() -> begin(admin, 123, "가".repeat(1001), UUID.randomUUID()), "INVALID_ADMIN_ACTION");
    verifyNoInteractions(eligibility);
  }

  @Test
  void auditBoundaryRejectsEnclosingBusinessTransaction() {
    assertThatThrownBy(() ->
            new TransactionTemplate(manager)
                .execute(ignored -> begin(admin, 123, "확인", UUID.randomUUID())))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  void databaseRejectsMismatchedActionTargetAndInvalidTerminalState() {
    long id = Long.parseLong(begin(admin, 123, "확인", UUID.randomUUID()).receipt().id());
    assertThatThrownBy(
            () -> jdbc.update("UPDATE admin_action_logs SET target_type='CONTRACT' WHERE id=?", id))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("ck_admin_action_pair");
    assertThatThrownBy(
            () -> jdbc.update("UPDATE admin_action_logs SET status='SUCCEEDED' WHERE id=?", id))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("ck_admin_action_done");
  }

  private BeginResult begin(long admin, long target, String reason, UUID key) {
    return service.begin(admin, TOPUP_RECHECK, target, reason, key);
  }

  private List<Object> concurrent(boolean sameRequest) throws Exception {
    var gate = new CountDownLatch(1);
    var key = UUID.randomUUID();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var futures = new ArrayList<java.util.concurrent.Future<Object>>();
      for (int i = 0; i < 2; i++) {
        final long actor = sameRequest || i == 0 ? admin : otherAdmin;
        futures.add(
            pool.submit(() -> {
              if (!gate.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Start timeout");
              try {
                return begin(actor, 123, "확인", sameRequest ? key : UUID.randomUUID());
              } catch (BusinessException ex) {
                return ex.code();
              }
            }));
      }
      gate.countDown();
      return List.of(
          futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
    }
  }

  private long member(boolean adminRole) {
    var member =
        members.saveAndFlush(
            Member.create(
                UUID.randomUUID() + "@example.com",
                "unused",
                "감사 검증",
                LocalDateTime.of(2026, 11, 10, 0, 0)));
    memberIds.add(member.getId());
    if (adminRole) jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", member.getId());
    return member.getId();
  }

  private void fails(Runnable call, String code) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            BusinessException.class, ex -> assertThat(ex.code()).isEqualTo(code));
  }
}
