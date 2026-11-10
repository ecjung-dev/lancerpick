package com.example.lancerpick.auth.mail;

import com.example.lancerpick.auth.repository.AccountChallengeRepository;
import com.example.lancerpick.common.time.UtcTimes;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Reconciles missing delivery results; never sends mail or issues/invalidates tokens. */
@Service
@Profile("!demo")
public class AccountMailReconciliationService {
  private final AccountChallengeRepository challenges;
  private final Clock clock;

  public AccountMailReconciliationService(AccountChallengeRepository challenges, Clock clock) {
    this.challenges = challenges;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public int reconcile(int staleMinutes, int limit) {
    if (staleMinutes < 1 || limit < 1 || limit > 100)
      throw new IllegalArgumentException("Invalid mail reconciliation bounds");
    var cutoff = UtcTimes.nowMicros(clock).minusMinutes(staleMinutes);
    if (cutoff.isBefore(LocalDateTime.of(1000, 1, 1, 0, 0))) return 0;
    var ids = challenges.stalePendingIds(cutoff, PageRequest.of(0, limit));
    return ids.isEmpty() ? 0 : challenges.markDeliveryUnknown(ids, cutoff);
  }
}
