package com.example.lancerpick.member;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.lancerpick.member.config.SecurityLockRetrySchedulingConfiguration;
import com.example.lancerpick.member.service.SecurityLockDelivery;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class SecurityLockRetrySchedulingTests {
  @Test
  void cursorAdvancesWrapsAndRetainsItsPositionWhenTheDatabaseFails() {
    var delivery = mock(SecurityLockDelivery.class);
    var scheduler = new SecurityLockRetrySchedulingConfiguration(delivery, 2);
    when(delivery.retryPendingAfter(0, 2)).thenReturn(new SecurityLockDelivery.RetryBatch(0, 10));
    when(delivery.retryPendingAfter(10, 2))
        .thenThrow(new IllegalStateException("private reason"))
        .thenReturn(new SecurityLockDelivery.RetryBatch(1, 11));
    when(delivery.retryPendingAfter(11, 2)).thenReturn(new SecurityLockDelivery.RetryBatch(0, 0));
    for (int i = 0; i < 5; i++) scheduler.retry();
    var calls = inOrder(delivery);
    calls.verify(delivery).retryPendingAfter(0, 2);
    calls.verify(delivery, times(2)).retryPendingAfter(10, 2);
    calls.verify(delivery).retryPendingAfter(11, 2);
    calls.verify(delivery).retryPendingAfter(0, 2);
  }

  @Test
  void overlappingInvocationsOnOneInstanceDoNotStartAnotherBatch() throws Exception {
    var delivery = mock(SecurityLockDelivery.class);
    var scheduler = new SecurityLockRetrySchedulingConfiguration(delivery, 1);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(delivery.retryPendingAfter(0, 1))
        .thenAnswer(call -> {
          entered.countDown();
          if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
          return new SecurityLockDelivery.RetryBatch(0, 0);
        });
    try (var pool = Executors.newSingleThreadExecutor()) {
      var first = pool.submit(scheduler::retry);
      try {
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
        scheduler.retry();
        verify(delivery, times(1)).retryPendingAfter(0, 1);
      } finally {
        release.countDown();
      }
      first.get(3, TimeUnit.SECONDS);
    }
  }

  private ApplicationContextRunner context(SecurityLockDelivery delivery) {
    return new ApplicationContextRunner()
        .withUserConfiguration(SecurityLockRetrySchedulingConfiguration.class)
        .withBean(SecurityLockDelivery.class, () -> delivery);
  }

  @Test
  void enabledByDefaultRunsOnItsNamedSchedulerAndClosesCleanly() {
    var delivery = mock(SecurityLockDelivery.class);
    var called = new CountDownLatch(1);
    when(delivery.retryPendingAfter(anyLong(), eq(1)))
        .thenAnswer(call -> {
          called.countDown();
          return new SecurityLockDelivery.RetryBatch(0, 0);
        });
    context(delivery)
        .withPropertyValues(
            "lancerpick.member.security-lock-retry.delay-ms=10",
            "lancerpick.member.security-lock-retry.batch-size=1")
        .run(ctx -> {
          assertThat(ctx)
              .hasNotFailed()
              .hasSingleBean(SecurityLockRetrySchedulingConfiguration.class);
          var worker = ctx.getBean("securityLockRetryScheduler", ThreadPoolTaskScheduler.class);
          assertThat(worker.getPoolSize()).isEqualTo(1);
          assertThat(worker.getThreadNamePrefix()).isEqualTo("security-lock-retry-");
          assertThat(called.await(5, TimeUnit.SECONDS)).isTrue();
        });
  }

  @Test
  void disabledAndDemoDoNotCreateTheSchedulerAndInvalidSizeFailsFast() {
    var delivery = mock(SecurityLockDelivery.class);
    context(delivery)
        .withPropertyValues("lancerpick.member.security-lock-retry.enabled=false")
        .run(ctx ->
            assertThat(ctx)
                .hasNotFailed()
                .doesNotHaveBean(SecurityLockRetrySchedulingConfiguration.class));
    context(delivery)
        .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("demo"))
        .run(ctx ->
            assertThat(ctx)
                .hasNotFailed()
                .doesNotHaveBean(SecurityLockRetrySchedulingConfiguration.class));
    for (int size : new int[] {0, 101})
      context(delivery)
          .withPropertyValues("lancerpick.member.security-lock-retry.batch-size=" + size)
          .run(ctx -> assertThat(ctx).hasFailed());
    verifyNoInteractions(delivery);
  }
}
