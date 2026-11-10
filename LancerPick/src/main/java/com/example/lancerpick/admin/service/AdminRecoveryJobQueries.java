package com.example.lancerpick.admin.service;

import static com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason.INVALID_JOB_PARAMETERS;
import static com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason.JOB_NOT_RESTARTABLE;
import static com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason.JOB_RUNNING;
import static com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason.NEWER_EXECUTION_EXISTS;
import static com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason.PROVIDER_DISABLED;
import static com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason.RECOVERY_DISABLED;

import com.example.lancerpick.admin.dto.AdminFinanceResponses;
import com.example.lancerpick.admin.dto.AdminFinanceResponses.Decision;
import com.example.lancerpick.wallet.config.TopupRecoveryBatchConfiguration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;

/**
 * 충전 복구 Batch 실행 조회와 재시작 가능 판단(스냅샷). {@link AdminFinanceQueryService}가 권한·입력 검사 뒤 같은 트랜잭션에서
 * 호출한다. 판단은 화면 표시용이며, 실제 재시작은 AdminBatchRestartService가 실행 시점에 다시 검사한다.
 */
@Component
@Profile("!demo")
public class AdminRecoveryJobQueries {
  private final JobRepository jobs;
  private final boolean recoveryEnabled;

  public AdminRecoveryJobQueries(
      JobRepository jobs,
      @Value("${lancerpick.wallet.topup-recovery.enabled:false}") boolean recoveryEnabled) {
    this.jobs = jobs;
    this.recoveryEnabled = recoveryEnabled;
  }

  long countInstances() {
    try {
      return jobs.getJobInstanceCount(TopupRecoveryBatchConfiguration.JOB_NAME);
    } catch (NoSuchJobException empty) {
      return 0;
    }
  }

  /** 한 JobInstance의 최신 실행만 돌려준다. offset은 호출자가 int 범위로 검사한다. */
  List<AdminFinanceResponses.RecoveryJob> latestPerInstance(int offset, int size, boolean available) {
    return jobs.getJobInstances(TopupRecoveryBatchConfiguration.JOB_NAME, offset, size).stream()
        .map(instance -> latest(instance, available))
        .toList();
  }

  /** 없는 실행이거나 충전 복구 Job의 실행이 아니면 빈 값. */
  Optional<AdminFinanceResponses.RecoveryJob> execution(long executionId, boolean available) {
    JobExecution execution;
    try {
      execution = jobs.getJobExecution(executionId);
    } catch (EmptyResultDataAccessException missing) {
      // Spring Batch 6.0.6은 없는 실행 ID에 null 대신 이 예외를 던진다(실행의 인스턴스 ID를 먼저 조회하는 단계).
      return Optional.empty();
    }
    if (execution == null
        || !TopupRecoveryBatchConfiguration.JOB_NAME.equals(
            execution.getJobInstance().getJobName())) {
      return Optional.empty();
    }
    return Optional.of(recoveryJob(execution, available));
  }

  private AdminFinanceResponses.RecoveryJob latest(JobInstance instance, boolean available) {
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
          Map.of(),
          Decision.blocked(JOB_NOT_RESTARTABLE));
    }
    var execution = jobs.getJobExecution(latest.getId());
    if (execution == null) {
      throw AdminFinanceQueryService.notFound();
    }
    return recoveryJob(execution, available);
  }

  private AdminFinanceResponses.RecoveryJob recoveryJob(JobExecution execution, boolean available) {
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
}
