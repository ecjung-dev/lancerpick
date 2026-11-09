package com.example.lancerpick.admin.service;

import static com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason.AUTOMATIC_LIMIT_REACHED;
import static com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason.INVALID_JOB_PARAMETERS;
import static com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason.JOB_NOT_RESTARTABLE;
import static com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason.JOB_RUNNING;
import static com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason.LEASE_ACTIVE;
import static com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason.NEWER_EXECUTION_EXISTS;
import static com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason.PAYMENT_NOT_IDENTIFIED;
import static com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason.PROVIDER_DISABLED;
import static com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason.RECOVERY_DISABLED;
import static com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason.RETRY_DELAY;
import static com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason.TERMINAL_STATE;
import static com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason.TOO_EARLY;

import com.example.lancerpick.admin.dto.AdminFinanceResponses;
import com.example.lancerpick.admin.dto.AdminFinanceResponses.Decision;
import com.example.lancerpick.admin.repository.AdminFinanceQueries;
import com.example.lancerpick.admin.repository.AdminFinanceQueries.PaymentFilter;
import com.example.lancerpick.admin.repository.AdminFinanceQueries.TopupFilter;
import com.example.lancerpick.admin.repository.AdminFinanceQueries.TopupRow;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.member.entity.MemberRole;
import com.example.lancerpick.wallet.config.TopupRecoveryBatchConfiguration;
import com.example.lancerpick.wallet.config.TopupRecoveryProperties;
import com.example.lancerpick.wallet.entity.WalletTopupStatus;
import com.example.lancerpick.wallet.gateway.TossPaymentGateway;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** K3/K6 연결용 내부 조회 계약. 모든 진입에서 현재 DB 권한을 검사한다. 재처리 판단은 스냅샷이며 금액/처리권/시도 수/Batch 상태를 변경하지 않는다. */
@Service
@Profile("!demo")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AdminFinanceQueryService {
  private static final Set<String> SAFE_ERRORS =
      Set.of(
          "PROVIDER_UNAVAILABLE",
          "PROVIDER_NOT_COMPLETED",
          "PROVIDER_TERMINAL_STATE",
          "RESULT_REQUIRES_REVIEW",
          "CREDIT_PENDING");
  private final AdminFinanceQueries queries;
  private final JobRepository jobs;
  private final TopupRecoveryProperties properties;
  private final TossPaymentGateway gateway;
  private final Clock clock;
  private final boolean recoveryEnabled;

  public AdminFinanceQueryService(
      AdminFinanceQueries queries,
      JobRepository jobs,
      TopupRecoveryProperties properties,
      TossPaymentGateway gateway,
      Clock clock,
      @Value("${lancerpick.wallet.topup-recovery.enabled:false}") boolean recoveryEnabled) {
    this.queries = queries;
    this.jobs = jobs;
    this.properties = properties;
    this.gateway = gateway;
    this.clock = clock;
    this.recoveryEnabled = recoveryEnabled;
  }

  public PageResponse<AdminFinanceResponses.Topup> topups(
      long adminId, TopupFilter filter, int page, int size) {
    requireAdmin(adminId);
    validatePage(page, size);
    var selected = filter == null ? new TopupFilter(null, null) : filter;
    positive(selected.memberId());
    long total = queries.countTopups(selected);
    Instant now = clock.instant();
    boolean providerAvailable = providerAvailable();
    return page(
        page,
        size,
        total,
        offset ->
            queries.topups(selected, offset, size).stream()
                .map(row -> topup(row, now, providerAvailable))
                .toList());
  }

  public AdminFinanceResponses.Topup topup(long adminId, long topupId) {
    requireAdmin(adminId);
    positive(topupId);
    var row = queries.topup(topupId).orElseThrow(AdminFinanceQueryService::notFound);
    return topup(row, clock.instant(), providerAvailable());
  }

  public PageResponse<AdminFinanceResponses.PaymentRecord> payments(
      long adminId, PaymentFilter filter, int page, int size) {
    requireAdmin(adminId);
    validatePage(page, size);
    var selected = filter == null ? new PaymentFilter(null, null, null, null, null) : filter;
    positive(selected.orderId());
    positive(selected.buyerId());
    positive(selected.sellerId());
    if (selected.status() != null && !Set.of("PAID", "REFUNDED").contains(selected.status())) {
      throw invalid();
    }
    long total = queries.countPayments(selected);
    return page(page, size, total, offset -> queries.payments(selected, offset, size));
  }

  public AdminFinanceResponses.OrderFinance orderFinance(long adminId, long orderId) {
    requireAdmin(adminId);
    positive(orderId);
    return queries.orderFinance(orderId).orElseThrow(AdminFinanceQueryService::notFound);
  }

  /** 한 JobInstance의 최신 실행만 목록에 표시한다. 상세 조회에서는 이전 실행도 검사할 수 있다. */
  public PageResponse<AdminFinanceResponses.RecoveryJob> recoveryJobs(
      long adminId, int page, int size) {
    requireAdmin(adminId);
    validatePage(page, size);
    if ((long) page * size > Integer.MAX_VALUE) {
      throw invalid();
    }
    long total;
    try {
      total = jobs.getJobInstanceCount(TopupRecoveryBatchConfiguration.JOB_NAME);
    } catch (NoSuchJobException empty) {
      total = 0;
    }
    boolean available = providerAvailable();
    return page(
        page,
        size,
        total,
        offset ->
            jobs
                .getJobInstances(
                    TopupRecoveryBatchConfiguration.JOB_NAME, Math.toIntExact(offset), size)
                .stream()
                .map(instance -> recoveryJobInstance(instance, available))
                .toList());
  }

  private AdminFinanceResponses.RecoveryJob recoveryJobInstance(
      JobInstance instance, boolean available) {
    var latest = jobs.getLastJobExecution(instance);
    if (latest == null) {
      // 인스턴스 저장 직후 프로세스가 종료되면 실행이 없을 수 있다. 조회 전체를 실패시키지 않는다.
      return new AdminFinanceResponses.RecoveryJob(
          null,
          Long.toString(instance.getInstanceId()),
          "NO_EXECUTION",
          null,
          0,
          0,
          0,
          java.util.Map.of(),
          Decision.blocked(JOB_NOT_RESTARTABLE));
    }
    return recoveryJob(jobs.getJobExecution(latest.getId()), available);
  }

  public AdminFinanceResponses.RecoveryJob recoveryJob(long adminId, long executionId) {
    requireAdmin(adminId);
    positive(executionId);
    org.springframework.batch.core.job.JobExecution execution;
    try {
      execution = jobs.getJobExecution(executionId);
    } catch (org.springframework.dao.EmptyResultDataAccessException missing) {
      // Spring Batch 6.0.6은 없는 실행 ID에 null 대신 이 예외를 던진다(실행의 인스턴스 ID를 먼저 조회하는 단계). 계약대로 404로 바꾼다.
      throw notFound();
    }
    if (execution == null
        || !TopupRecoveryBatchConfiguration.JOB_NAME.equals(
            execution.getJobInstance().getJobName())) {
      throw notFound();
    }
    return recoveryJob(execution, providerAvailable());
  }

  private AdminFinanceResponses.Topup topup(TopupRow row, Instant now, boolean available) {
    Decision manual = manualDecision(row, now, available);
    Decision automatic = automaticDecision(row, manual);
    String safeCode =
        row.lastErrorCode() == null
            ? null
            : SAFE_ERRORS.contains(row.lastErrorCode())
                ? row.lastErrorCode()
                : "RESULT_REQUIRES_REVIEW";
    return new AdminFinanceResponses.Topup(
        Long.toString(row.id()),
        Long.toString(row.memberId()),
        row.amount(),
        row.status(),
        safeCode,
        row.attempts(),
        at(row.createdAt()),
        at(row.processingStartedAt()),
        at(row.nextRecoveryAt()),
        at(row.leaseUntil()),
        at(row.completedAt()),
        automatic,
        manual);
  }

  private Decision manualDecision(TopupRow row, Instant now, boolean available) {
    if (row.status() == WalletTopupStatus.COMPLETED || row.status() == WalletTopupStatus.FAILED) {
      return Decision.blocked(TERMINAL_STATE);
    }
    if (row.status() == WalletTopupStatus.READY
        || !row.identified()
        || row.processingStartedAt() == null) {
      return Decision.blocked(PAYMENT_NOT_IDENTIFIED);
    }
    if (row.leaseUntil() != null && now.isBefore(row.leaseUntil())) {
      return Decision.blocked(LEASE_ACTIVE);
    }
    if (row.processingStartedAt().isAfter(now.minus(properties.staleAge()))) {
      return Decision.blocked(TOO_EARLY);
    }
    if (row.nextRecoveryAt() != null && now.isBefore(row.nextRecoveryAt())) {
      return Decision.blocked(RETRY_DELAY);
    }
    return available ? Decision.ready() : Decision.blocked(PROVIDER_DISABLED);
  }

  private Decision automaticDecision(TopupRow row, Decision manual) {
    if (!manual.eligible()) {
      return manual;
    }
    if (!recoveryEnabled) {
      return Decision.blocked(RECOVERY_DISABLED);
    }
    if (row.attempts() >= properties.maxAttempts()) {
      return Decision.blocked(AUTOMATIC_LIMIT_REACHED);
    }
    return Decision.ready();
  }

  private AdminFinanceResponses.RecoveryJob recoveryJob(JobExecution execution, boolean available) {
    if (execution == null) {
      throw notFound();
    }
    var latest = jobs.getLastJobExecution(execution.getJobInstance());
    var status = execution.getStatus();
    OffsetDateTime requestedAt = requestedAt(execution);
    Decision decision = restartDecision(execution, latest, requestedAt, available);
    long read = 0;
    long credited = 0;
    long filtered = 0;
    var outcomes = new LinkedHashMap<String, Long>();
    for (String name : List.of("completed", "failed", "deferred", "exhausted", "stale")) {
      outcomes.put(name, 0L);
    }
    for (var step : execution.getStepExecutions()) {
      read += step.getReadCount();
      credited += step.getWriteCount();
      filtered += step.getFilterCount();
      for (String name : outcomes.keySet()) {
        Object count = step.getExecutionContext().get(name);
        if (count instanceof Long value && value >= 0) {
          outcomes.put(name, outcomes.get(name) + value);
        }
      }
    }
    return new AdminFinanceResponses.RecoveryJob(
        Long.toString(execution.getId()),
        Long.toString(execution.getJobInstanceId()),
        status.name(),
        requestedAt,
        read,
        credited,
        filtered,
        outcomes,
        decision);
  }

  private Decision restartDecision(
      JobExecution execution, JobExecution latest, OffsetDateTime requestedAt, boolean available) {
    if (!recoveryEnabled) {
      return Decision.blocked(RECOVERY_DISABLED);
    }
    if (requestedAt == null) {
      return Decision.blocked(INVALID_JOB_PARAMETERS);
    }
    if (latest == null || latest.getId() != execution.getId()) {
      return Decision.blocked(NEWER_EXECUTION_EXISTS);
    }
    var status = execution.getStatus();
    if (Set.of(BatchStatus.STARTING, BatchStatus.STARTED, BatchStatus.STOPPING).contains(status)) {
      return Decision.blocked(JOB_RUNNING);
    }
    if (!Set.of(BatchStatus.FAILED, BatchStatus.STOPPED).contains(status)) {
      return Decision.blocked(JOB_NOT_RESTARTABLE);
    }
    return available ? Decision.ready() : Decision.blocked(PROVIDER_DISABLED);
  }

  private static OffsetDateTime requestedAt(JobExecution execution) {
    try {
      var parameters = execution.getJobParameters();
      if (parameters.parameters().size() != 2) {
        return null;
      }
      UUID.fromString(parameters.getString("run.id"));
      Long millis = parameters.getLong("requestedAtMillis");
      if (millis == null || millis < 0) {
        return null;
      }
      var at = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC);
      return at.getYear() <= 9999 ? at : null;
    } catch (RuntimeException invalid) {
      return null;
    }
  }

  private boolean providerAvailable() {
    try {
      gateway.requireAvailable(); // 설정 확인만 수행한다. 외부 통신은 하지 않는다.
      return true;
    } catch (BusinessException disabled) {
      return false;
    }
  }

  private void requireAdmin(long id) {
    if (id <= 0) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    }
    var role =
        queries
            .memberRole(id)
            .orElseThrow(() ->
                new BusinessException(
                    HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."));
    if (role != MemberRole.ADMIN) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "관리자 권한이 필요합니다.");
    }
  }

  private static void positive(Long id) {
    if (id != null && id <= 0) {
      throw invalid();
    }
  }

  private static void validatePage(int page, int size) {
    if (page < 0 || size < 1 || size > 100) {
      throw invalid();
    }
  }

  private static <T> PageResponse<T> page(
      int page, int size, long total, Function<Long, List<T>> loader) {
    long offset = (long) page * size;
    var items = offset >= total ? List.<T>of() : loader.apply(offset);
    return new PageResponse<>(
        items, page, size, total, Math.toIntExact(total / size + (total % size == 0 ? 0 : 1)));
  }

  private static OffsetDateTime at(Instant value) {
    return value == null ? null : value.atOffset(ZoneOffset.UTC);
  }

  private static BusinessException invalid() {
    return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "조회 조건을 확인해 주세요.");
  }

  private static BusinessException notFound() {
    return new BusinessException(
        HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "금융 처리 기록을 찾을 수 없습니다.");
  }
}
