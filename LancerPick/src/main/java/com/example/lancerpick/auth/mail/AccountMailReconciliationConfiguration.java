package com.example.lancerpick.auth.mail;

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

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile("!demo")
@ConditionalOnProperty(
    name = "lancerpick.mail.reconciliation.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AccountMailReconciliationConfiguration {
  private static final Logger log =
      LoggerFactory.getLogger(AccountMailReconciliationConfiguration.class);
  private final AccountMailReconciliationService reconciliation;
  private final int staleMinutes;
  private final int batchSize;

  public AccountMailReconciliationConfiguration(
      AccountMailReconciliationService reconciliation,
      @Value("${lancerpick.mail.reconciliation.stale-minutes:30}") int staleMinutes,
      @Value("${lancerpick.mail.reconciliation.batch-size:50}") int batchSize) {
    if (staleMinutes < 1 || batchSize < 1 || batchSize > 100)
      throw new IllegalArgumentException("Invalid mail reconciliation bounds");
    this.reconciliation = reconciliation;
    this.staleMinutes = staleMinutes;
    this.batchSize = batchSize;
  }

  @Bean(defaultCandidate = false)
  public ThreadPoolTaskScheduler accountMailReconciliationScheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("mail-reconciliation-");
    return scheduler;
  }

  @Scheduled(
      scheduler = "accountMailReconciliationScheduler",
      initialDelayString = "${lancerpick.mail.reconciliation.delay-ms:60000}",
      fixedDelayString = "${lancerpick.mail.reconciliation.delay-ms:60000}")
  public void reconcile() {
    try {
      int changed = reconciliation.reconcile(staleMinutes, batchSize);
      if (changed > 0) log.info("Unconfirmed mail delivery results reconciled count={}", changed);
    } catch (RuntimeException failure) {
      log.warn(
          "Mail reconciliation deferred code=MAIL_RECONCILIATION_FAILED type={}",
          failure.getClass().getSimpleName());
    }
  }
}
