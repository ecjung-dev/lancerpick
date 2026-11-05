package com.example.lancerpick.notification.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 아웃박스를 주기적으로 처리한다. 통합 테스트처럼 DB 정리를 직접 하는 환경에서는
 * lancerpick.notifications.dispatch-enabled=false로 끄고 OutboxDispatcher를 직접 호출한다.
 */
@Configuration
@EnableScheduling
@Profile("!demo")
@ConditionalOnProperty(
    name = "lancerpick.notifications.dispatch-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxDispatchScheduler {
  private final OutboxDispatcher dispatcher;

  public OutboxDispatchScheduler(OutboxDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Scheduled(
      initialDelayString = "${lancerpick.notifications.dispatch-delay-ms:2000}",
      fixedDelayString = "${lancerpick.notifications.dispatch-delay-ms:2000}")
  void dispatch() {
    dispatcher.dispatchDue(100);
  }
}
