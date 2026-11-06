package com.example.lancerpick.wallet.service;

import com.example.lancerpick.wallet.config.TopupRecoveryBatchConfiguration;
import java.time.Clock;
import java.util.UUID;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 내부 실행 계약. 관리자 API가 추가되면 호출자의 권한·사유·운영 이력은 관리자 영역에서 검사한다. */
@Service
@Profile("!demo")
@ConditionalOnProperty(name = "lancerpick.wallet.topup-recovery.enabled", havingValue = "true")
@Transactional(propagation = Propagation.NEVER)
public class TopupRecoveryJobService {
  private final JobOperator operator;
  private final JobRepository repository;
  private final Job job;
  private final Clock clock;

  public TopupRecoveryJobService(
      JobOperator operator,
      JobRepository repository,
      @Qualifier("walletTopupRecoveryJob") Job job,
      Clock clock) {
    this.operator = operator;
    this.repository = repository;
    this.job = job;
    this.clock = clock;
  }

  public JobExecution start() throws Exception {
    return operator.start(
        job,
        new JobParametersBuilder()
            .addString("run.id", UUID.randomUUID().toString())
            .addLong("requestedAtMillis", clock.millis())
            .toJobParameters());
  }

  public JobExecution restart(long executionId) throws Exception {
    var execution = repository.getJobExecution(executionId);
    if (execution == null
        || !TopupRecoveryBatchConfiguration.JOB_NAME.equals(
            execution.getJobInstance().getJobName())) {
      throw new IllegalArgumentException("Unknown top-up recovery execution");
    }
    return operator.restart(execution);
  }
}
