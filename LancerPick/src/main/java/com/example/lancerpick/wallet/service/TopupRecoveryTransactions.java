package com.example.lancerpick.wallet.service;

import com.example.lancerpick.wallet.config.TopupRecoveryProperties;
import com.example.lancerpick.wallet.entity.WalletTopupStatus;
import com.example.lancerpick.wallet.gateway.TossPaymentGateway.Payment;
import com.example.lancerpick.wallet.repository.WalletTopupRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 충전 잠금 아래 처리권을 확인하고 기존 지급 트랜잭션을 재사용한다. 외부 통신은 하지 않는다. */
@Service
@Profile("!demo")
@ConditionalOnProperty(name = "lancerpick.wallet.topup-recovery.enabled", havingValue = "true")
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class TopupRecoveryTransactions {
  private final WalletTopupRepository topups;
  private final TopupApprovalTransactions approvals;
  private final TopupRecoveryProperties properties;
  private final Clock clock;

  public record Claim(long topupId, long memberId, String token, String paymentKey) {
    @Override
    public String toString() {
      return "TopupRecoveryClaim[topupId=" + topupId + ", credentials=***]";
    }
  }

  public enum Result {
    COMPLETED,
    FAILED,
    DEFERRED,
    EXHAUSTED,
    STALE
  }

  public Optional<Claim> claim(long id, Instant staleBefore) {
    var topup = topups.findByIdForUpdate(id).orElse(null);
    Instant now = clock.instant();
    if (topup == null || !topup.canRecover(now, staleBefore, properties.maxAttempts())) {
      return Optional.empty();
    }
    UUID token = UUID.randomUUID();
    topup.claimRecovery(token, now, properties.leaseDuration());
    topups.flush();
    return Optional.of(new Claim(id, topup.getMemberId(), token.toString(), topup.getPaymentKey()));
  }

  public Result apply(Claim claim, Payment payment) {
    var topup = topups.findByIdForUpdate(claim.topupId()).orElse(null);
    if (topup == null || !topup.ownsRecovery(claim.token(), clock.instant())) {
      return Result.STALE;
    }
    var response = approvals.apply(claim.memberId(), claim.topupId(), payment);
    if (response.status() == WalletTopupStatus.COMPLETED) {
      return Result.COMPLETED;
    }
    if (response.status() == WalletTopupStatus.FAILED) {
      return Result.FAILED;
    }
    return defer(claim, "PROVIDER_NOT_COMPLETED");
  }

  public Result defer(Claim claim, String safeCode) {
    var topup = topups.findByIdForUpdate(claim.topupId()).orElse(null);
    if (topup == null || !topup.ownsRecovery(claim.token(), clock.instant())) {
      return Result.STALE;
    }
    Instant now = clock.instant();
    boolean exhausted = topup.getRecoveryAttemptCount() >= properties.maxAttempts();
    topup.deferRecovery(
        safeCode,
        exhausted ? null : now.plus(properties.retryDelay(topup.getRecoveryAttemptCount())),
        now);
    topups.flush();
    return exhausted ? Result.EXHAUSTED : Result.DEFERRED;
  }
}
