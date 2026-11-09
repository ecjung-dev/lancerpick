package com.example.lancerpick.order.service;

import com.example.lancerpick.member.dto.SecurityLockApplied;
import com.example.lancerpick.member.service.SecurityLockTimerStopper;
import com.example.lancerpick.order.repository.OrderResponseTimerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매자 보안 잠금이 커밋된 뒤 그 구매자의 진행 중 자동 구매 확정 타이머를 중단한다(U8). 잠금 적용 시각까지 시작된 타이머만 대상이며,
 * 타이머마다 별도 트랜잭션({@link ReviewTimerService#stopForSecurityLock})으로 처리해 한 주문의 실패가 다른 주문을 막지 않는다.
 * 하나라도 실패하면 예외로 알려 전달 기록을 미처리로 남긴다. 실패분도 만료 처리의 재검사로 자동 확정되지 않는다.
 */
@Component
@Profile("!demo")
@Transactional(propagation = Propagation.NEVER)
public class SecurityLockTimerStops implements SecurityLockTimerStopper {
  private static final Logger log = LoggerFactory.getLogger(SecurityLockTimerStops.class);

  private final OrderResponseTimerRepository timers;
  private final ReviewTimerService reviewTimers;

  public SecurityLockTimerStops(
      OrderResponseTimerRepository timers, ReviewTimerService reviewTimers) {
    this.timers = timers;
    this.reviewTimers = reviewTimers;
  }

  @Override
  public void stop(SecurityLockApplied lock) {
    int failed = 0;
    for (long timerId : timers.activeTimerIdsOfBuyerStartedBy(lock.memberId(), lock.appliedAt())) {
      try {
        reviewTimers.stopForSecurityLock(timerId);
      } catch (RuntimeException e) {
        failed++;
        log.warn(
            "Security lock timer stop failed: sanctionId={} timerId={} type={}",
            lock.sanctionId(),
            timerId,
            e.getClass().getSimpleName());
      }
    }
    if (failed > 0)
      throw new IllegalStateException("Security lock timer stop incomplete: " + failed);
  }
}
