package com.example.lancerpick.order.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 만료된 자동 구매 확정 타이머를 주기적으로 처리한다. 통합 테스트는 lancerpick.order.review-timer.expiry-enabled=false로
 * 끄고 ReviewTimerService.expireDue를 직접 호출한다. 여러 서버가 동시에 실행해도 주문 잠금 후 재확인하므로 한 번만 지급된다.
 */
@Configuration
@EnableScheduling
@Profile("!demo")
@ConditionalOnProperty(
    name = "lancerpick.order.review-timer.expiry-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ReviewTimerExpiryScheduler {
  private final ReviewTimerService reviewTimers;

  public ReviewTimerExpiryScheduler(ReviewTimerService reviewTimers) {
    this.reviewTimers = reviewTimers;
  }

  @Scheduled(
      initialDelayString = "${lancerpick.order.review-timer.expiry-delay-ms:30000}",
      fixedDelayString = "${lancerpick.order.review-timer.expiry-delay-ms:30000}")
  void expireDue() {
    reviewTimers.expireDue(50);
  }
}
