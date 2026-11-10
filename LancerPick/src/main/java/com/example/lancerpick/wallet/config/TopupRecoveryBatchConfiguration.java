package com.example.lancerpick.wallet.config;

import com.example.lancerpick.wallet.batch.TopupRecoveryJobParameters;
import com.example.lancerpick.wallet.batch.TopupRecoveryTasklet;
import com.example.lancerpick.wallet.repository.TopupRecoveryQueries;
import com.example.lancerpick.wallet.service.TopupRecoveryService;
import java.time.Clock;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@Profile("!demo")
@ConditionalOnProperty(name = "lancerpick.wallet.topup-recovery.enabled", havingValue = "true")
@EnableConfigurationProperties(TopupRecoveryProperties.class)
public class TopupRecoveryBatchConfiguration {
  public static final String JOB_NAME = "walletTopupRecovery";

  @Bean
  public TopupRecoveryTasklet topupRecoveryTasklet(
      TopupRecoveryQueries queries,
      TopupRecoveryService recovery,
      TopupRecoveryProperties properties,
      Clock clock) {
    return new TopupRecoveryTasklet(queries, recovery, properties, clock);
  }

  @Bean
  public Step walletTopupRecoveryStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      TopupRecoveryTasklet tasklet) {
    // Step 체크포인트는 기본 트랜잭션으로 저장한다. 복구 서비스는 이를 일시 중단한 뒤
    // 처리권 확보/외부 조회/지급을 분리하므로 외부 응답을 기다리며 주문·지갑 잠금을 보유하지 않는다.
    return new StepBuilder("walletTopupRecoveryStep", jobRepository)
        .tasklet(tasklet, transactionManager)
        .build();
  }

  @Bean
  public Job walletTopupRecoveryJob(JobRepository jobRepository, Step walletTopupRecoveryStep) {
    return new JobBuilder(JOB_NAME, jobRepository)
        .validator(TopupRecoveryJobParameters::validate)
        .start(walletTopupRecoveryStep)
        .build();
  }
}
