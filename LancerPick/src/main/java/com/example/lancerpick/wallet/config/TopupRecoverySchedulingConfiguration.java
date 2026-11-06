package com.example.lancerpick.wallet.config;

import com.example.lancerpick.wallet.service.TopupRecoveryJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@Profile("!demo")
@EnableScheduling
@ConditionalOnProperty(
    name = {
      "lancerpick.wallet.topup-recovery.enabled",
      "lancerpick.wallet.topup-recovery.scheduling-enabled"
    },
    havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class TopupRecoverySchedulingConfiguration {
  private final TopupRecoveryJobService jobs;

  // 이름으로 지정한 복구 작업만 사용한다. 다른 @Scheduled 작업의 기본 후보가 되지 않는다.
  @Bean(defaultCandidate = false)
  public ThreadPoolTaskScheduler topupRecoveryScheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("topup-recovery-");
    return scheduler;
  }

  @Scheduled(
      scheduler = "topupRecoveryScheduler",
      initialDelayString = "${lancerpick.wallet.topup-recovery.delay-ms:60000}",
      fixedDelayString = "${lancerpick.wallet.topup-recovery.delay-ms:60000}")
  public void run() {
    try {
      var execution = jobs.start();
      log.info(
          "Top-up recovery job executionId={} status={}", execution.getId(), execution.getStatus());
    } catch (Exception failure) {
      log.warn("Top-up recovery launch failed code=RECOVERY_LAUNCH_FAILED");
    }
  }
}
