package com.example.lancerpick.wallet.repository;

import com.example.lancerpick.wallet.entity.QWalletTopup;
import com.example.lancerpick.wallet.entity.WalletTopupStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!demo")
@ConditionalOnProperty(name = "lancerpick.wallet.topup-recovery.enabled", havingValue = "true")
@Transactional(readOnly = true)
public class TopupRecoveryQueries {
  private final JPAQueryFactory query;
  private static final QWalletTopup topup = QWalletTopup.walletTopup;

  public TopupRecoveryQueries(EntityManager entityManager) {
    query = new JPAQueryFactory(entityManager);
  }

  public long highestId() {
    Long highest = query.select(topup.id.max()).from(topup).fetchOne();
    return highest == null ? 0 : highest;
  }

  public Optional<Long> nextId(
      long afterId, long highestId, Instant now, Instant staleBefore, int maxAttempts) {
    return Optional.ofNullable(
        query
            .select(topup.id)
            .from(topup)
            .where(
                topup.id.gt(afterId),
                topup.id.loe(highestId),
                topup.status.in(WalletTopupStatus.PROCESSING, WalletTopupStatus.UNKNOWN),
                topup.paymentKey.isNotNull(),
                topup.processingStartedAt.loe(staleBefore),
                topup.recoveryAttemptCount.lt(maxAttempts),
                topup.nextRecoveryAt.isNull().or(topup.nextRecoveryAt.loe(now)),
                topup.recoveryLeaseUntil.isNull().or(topup.recoveryLeaseUntil.loe(now)))
            .orderBy(topup.id.asc())
            .fetchFirst());
  }
}
