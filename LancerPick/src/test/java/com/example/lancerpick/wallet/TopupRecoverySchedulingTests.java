package com.example.lancerpick.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.lancerpick.wallet.config.TopupRecoverySchedulingConfiguration;
import com.example.lancerpick.wallet.service.TopupRecoveryJobService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class TopupRecoverySchedulingTests {
  private final TopupRecoveryJobService jobs = mock(TopupRecoveryJobService.class);
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(TopupRecoverySchedulingConfiguration.class)
          .withBean(TopupRecoveryJobService.class, () -> jobs);

  @Test
  void disabledByDefaultAndEnablingOnlyJobNeverStartsTheScheduler() {
    runner.run(context -> assertThat(context).doesNotHaveBean("topupRecoveryScheduler"));
    runner
        .withPropertyValues("lancerpick.wallet.topup-recovery.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean("topupRecoveryScheduler"));
    runner
        .withPropertyValues("lancerpick.wallet.topup-recovery.scheduling-enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean("topupRecoveryScheduler"));
    verifyNoInteractions(jobs);
  }

  @Test
  void explicitOptInRegistersAnIndependentSingleWorkerScheduleWithoutBeanNameConflict() {
    runner
        .withPropertyValues(
            "lancerpick.wallet.topup-recovery.enabled=true",
            "lancerpick.wallet.topup-recovery.scheduling-enabled=true",
            "lancerpick.wallet.topup-recovery.delay-ms=3600000")
        .run(context -> {
          assertThat(context).hasNotFailed();
          var scheduler = context.getBean("topupRecoveryScheduler", ThreadPoolTaskScheduler.class);
          assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
          assertThat(scheduler.getThreadNamePrefix()).isEqualTo("topup-recovery-");
          assertThat(
                  context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks())
              .hasSize(1);
          verifyNoInteractions(jobs);
        });
  }

  @Test
  void demoProfileDoesNotRegisterRecoveryScheduleEvenWhenFlagsAreEnabled() {
    runner
        .withPropertyValues(
            "spring.profiles.active=demo",
            "lancerpick.wallet.topup-recovery.enabled=true",
            "lancerpick.wallet.topup-recovery.scheduling-enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean("topupRecoveryScheduler"));
    verifyNoInteractions(jobs);
  }

  @Test
  void enablingRecoveryDoesNotReplaceTheExistingDefaultScheduler() {
    runner
        .withBean("existingScheduler", ThreadPoolTaskScheduler.class, ThreadPoolTaskScheduler::new)
        .withPropertyValues(
            "lancerpick.wallet.topup-recovery.enabled=true",
            "lancerpick.wallet.topup-recovery.scheduling-enabled=true",
            "lancerpick.wallet.topup-recovery.delay-ms=3600000")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(TaskScheduler.class))
              .isSameAs(context.getBean("existingScheduler"))
              .isNotSameAs(context.getBean("topupRecoveryScheduler"));
          verifyNoInteractions(jobs);
        });
  }
}
