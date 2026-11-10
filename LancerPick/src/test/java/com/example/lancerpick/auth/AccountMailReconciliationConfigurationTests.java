package com.example.lancerpick.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.lancerpick.auth.mail.AccountMailReconciliationConfiguration;
import com.example.lancerpick.auth.mail.AccountMailReconciliationService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class AccountMailReconciliationConfigurationTests {
  private ApplicationContextRunner context(AccountMailReconciliationService service) {
    return new ApplicationContextRunner()
        .withUserConfiguration(AccountMailReconciliationConfiguration.class)
        .withBean(AccountMailReconciliationService.class, () -> service);
  }

  @Test
  void runsOnItsOwnSchedulerWithoutRequiringAnSmtpSender() {
    var service = mock(AccountMailReconciliationService.class);
    var called = new CountDownLatch(1);
    when(service.reconcile(30, 50))
        .thenAnswer(inv -> {
          called.countDown();
          return 0;
        });
    context(service)
        .withPropertyValues("lancerpick.mail.reconciliation.delay-ms=10")
        .run(ctx -> {
          assertThat(ctx).hasNotFailed();
          var scheduler =
              ctx.getBean("accountMailReconciliationScheduler", ThreadPoolTaskScheduler.class);
          assertThat(scheduler.getThreadNamePrefix()).isEqualTo("mail-reconciliation-");
          assertThat(scheduler.getPoolSize()).isEqualTo(1);
          assertThat(called.await(5, TimeUnit.SECONDS)).isTrue();
        });
  }

  @Test
  void disabledAndDemoCreateNoWorkerAndInvalidBoundsFailStartup() {
    var service = mock(AccountMailReconciliationService.class);
    context(service)
        .withPropertyValues("lancerpick.mail.reconciliation.enabled=false")
        .run(ctx ->
            assertThat(ctx)
                .hasNotFailed()
                .doesNotHaveBean(AccountMailReconciliationConfiguration.class));
    context(service)
        .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("demo"))
        .run(ctx ->
            assertThat(ctx)
                .hasNotFailed()
                .doesNotHaveBean(AccountMailReconciliationConfiguration.class));
    for (String setting : new String[] {"stale-minutes=0", "batch-size=0", "batch-size=101"})
      context(service)
          .withPropertyValues("lancerpick.mail.reconciliation." + setting)
          .run(ctx -> assertThat(ctx).hasFailed());
    verifyNoInteractions(service);
  }

  @Test
  void aDatabaseFailureDoesNotDisableTheNextInvocation() {
    var service = mock(AccountMailReconciliationService.class);
    when(service.reconcile(30, 50))
        .thenThrow(new IllegalStateException("private body"))
        .thenReturn(1);
    var job = new AccountMailReconciliationConfiguration(service, 30, 50);
    assertThatCode(job::reconcile).doesNotThrowAnyException();
    job.reconcile();
    verify(service, times(2)).reconcile(30, 50);
  }
}
