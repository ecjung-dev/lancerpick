package com.example.lancerpick.wallet.service;

import com.example.lancerpick.admin.entity.AdminAction;
import com.example.lancerpick.admin.service.AdminActionAuditService.Outcome;
import com.example.lancerpick.admin.service.AdminActionExecutionGuard;
import com.example.lancerpick.wallet.config.TopupRecoveryProperties;
import com.example.lancerpick.wallet.entity.WalletTopupStatus;
import com.example.lancerpick.wallet.gateway.TossPaymentGateway.Payment;
import com.example.lancerpick.wallet.repository.WalletTopupRepository;
import com.example.lancerpick.wallet.service.TopupRecoveryTransactions.Claim;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 단건 조회는 Batch 활성화와 무관하다. 처리권/지급은 기존 충전 행과 원장을 공유한다. */
@Service
@Profile("!demo")
@Transactional(isolation = Isolation.READ_COMMITTED)
public class AdminTopupRecheckTransactions {
  private final AdminActionExecutionGuard guard;
  private final WalletTopupRepository topups;
  private final TopupApprovalTransactions approvals;
  private final TopupRecoveryProperties properties;
  private final Clock clock;

  public AdminTopupRecheckTransactions(
      AdminActionExecutionGuard guard,
      WalletTopupRepository topups,
      TopupApprovalTransactions approvals,
      TopupRecoveryProperties properties,
      Clock clock) {
    this.guard = guard;
    this.topups = topups;
    this.approvals = approvals;
    this.properties = properties;
    this.clock = clock;
  }

  public Optional<Claim> claim(long adminId, long logId, long topupId) {
    guard.require(adminId, logId, AdminAction.TOPUP_RECHECK, topupId);
    var topup = topups.findByIdForUpdate(topupId).orElse(null);
    var now = clock.instant();
    if (topup == null || !topup.canManuallyRecover(now, now.minus(properties.staleAge()))) {
      return Optional.empty();
    }
    var token = UUID.randomUUID();
    topup.claimManualRecovery(token, now, properties.leaseDuration());
    topups.flush();
    return Optional.of(
        new Claim(topupId, topup.getMemberId(), token.toString(), topup.getPaymentKey()));
  }

  public Outcome apply(Claim claim, Payment payment) {
    var topup = topups.findByIdForUpdate(claim.topupId()).orElse(null);
    if (topup == null || !topup.ownsRecovery(claim.token(), clock.instant()))
      return Outcome.TOPUP_STATE_CHANGED;
    // 실제 충전 소유자를 서버 저장값에서 얻는다. 구매자 API의 인증 ID로 가장하지 않는다.
    var response = approvals.apply(claim.memberId(), claim.topupId(), payment);
    if (response.status() == WalletTopupStatus.COMPLETED) return Outcome.TOPUP_CREDITED;
    if (response.status() == WalletTopupStatus.FAILED) return Outcome.TOPUP_RESOLVED_FAILED;
    return defer(claim, "PROVIDER_NOT_COMPLETED");
  }

  public Outcome defer(Claim claim, String safeCode) {
    var topup = topups.findByIdForUpdate(claim.topupId()).orElse(null);
    if (topup == null || !topup.ownsRecovery(claim.token(), clock.instant()))
      return Outcome.TOPUP_STATE_CHANGED;
    var now = clock.instant();
    topup.deferRecovery(
        safeCode, now.plus(properties.retryDelay(topup.getRecoveryAttemptCount())), now);
    topups.flush();
    return Outcome.TOPUP_RESULT_UNCONFIRMED;
  }
}
