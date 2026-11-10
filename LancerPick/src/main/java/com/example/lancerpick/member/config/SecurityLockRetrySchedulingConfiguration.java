package com.example.lancerpick.member.config;

import com.example.lancerpick.member.service.SecurityLockDelivery;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** DB-backed at-least-once retry; timer stops remain idempotent under the existing order locks. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile("!demo")
@ConditionalOnProperty(
    name = "lancerpick.member.security-lock-retry.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SecurityLockRetrySchedulingConfiguration {
  private static final Logger log =
      LoggerFactory.getLogger(SecurityLockRetrySchedulingConfiguration.class);
  private final SecurityLockDelivery delivery;
  private final int batchSize;
  private final AtomicBoolean running = new AtomicBoolean();
  // Progress is only an optimization. Pending rows survive restart and are scanned again from zero.
  private long afterId;

  public SecurityLockRetrySchedulingConfiguration(
      SecurityLockDelivery delivery,
      @Value("${lancerpick.member.security-lock-retry.batch-size:50}") int batchSize) {
    if (batchSize < 1 || batchSize > 100)
      throw new IllegalArgumentException("Retry batch size must be 1..100");
    this.delivery = delivery;
    this.batchSize = batchSize;
  }

  @Bean(defaultCandidate = false)
  public ThreadPoolTaskScheduler securityLockRetryScheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("security-lock-retry-");
    scheduler.setWaitForTasksToCompleteOnShutdown(false);
    return scheduler;
  }

  @Scheduled(
      scheduler = "securityLockRetryScheduler",
      initialDelayString = "${lancerpick.member.security-lock-retry.delay-ms:30000}",
      fixedDelayString = "${lancerpick.member.security-lock-retry.delay-ms:30000}")
  public void retry() {
    if (!running.compareAndSet(false, true)) return;
    try {
      var result = delivery.retryPendingAfter(afterId, batchSize);
      afterId = result.nextId();
      if (result.delivered() > 0) log.info("Security lock retry delivered={}", result.delivered());
    } catch (RuntimeException failure) {
      log.warn(
          "Security lock retry deferred code=SECURITY_LOCK_RETRY_FAILED type={}",
          failure.getClass().getSimpleName());
    } finally {
      running.set(false);
    }
  }
}
