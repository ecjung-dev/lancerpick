package com.example.lancerpick.wallet.service;

import com.example.lancerpick.admin.service.AdminActionAuditService.Outcome;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.wallet.gateway.TossGatewayException;
import com.example.lancerpick.wallet.gateway.TossPaymentGateway;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 감사 요청에 연결된 관리자 전용 입구. 새 승인 요청 없이 저장된 키로 조회만 한다. */
@Service
@Profile("!demo")
@Transactional(propagation = Propagation.NEVER)
public class AdminTopupRecheckService {
  private final AdminTopupRecheckTransactions transactions;
  private final TossPaymentGateway gateway;

  public AdminTopupRecheckService(
      AdminTopupRecheckTransactions transactions, TossPaymentGateway gateway) {
    this.transactions = transactions;
    this.gateway = gateway;
  }

  public Outcome recheck(long adminId, long logId, long topupId) {
    var optional = transactions.claim(adminId, logId, topupId);
    if (optional.isEmpty()) return Outcome.TOPUP_STATE_CHANGED;
    var claim = optional.get();
    try {
      gateway.requireAvailable();
      return transactions.apply(claim, gateway.lookup(claim.paymentKey()));
    } catch (TossGatewayException unavailable) {
      transactions.defer(claim, "PROVIDER_UNAVAILABLE");
      return Outcome.TOPUP_RESULT_UNCONFIRMED;
    } catch (BusinessException mismatch) {
      transactions.defer(claim, "RESULT_REQUIRES_REVIEW");
      return Outcome.TOPUP_RESULT_UNCONFIRMED;
    } catch (RuntimeException uncertainCommit) {
      // 커밋 성공 여부를 알 수 없으면 지급 실패로 확정하지 않는다.
      transactions.defer(claim, "CREDIT_PENDING");
      return Outcome.TOPUP_RESULT_UNCONFIRMED;
    }
  }
}
