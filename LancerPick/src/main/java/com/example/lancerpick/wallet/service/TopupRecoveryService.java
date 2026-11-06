package com.example.lancerpick.wallet.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.wallet.gateway.TossGatewayException;
import com.example.lancerpick.wallet.gateway.TossPaymentGateway;
import com.example.lancerpick.wallet.service.TopupRecoveryTransactions.Result;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")
@ConditionalOnProperty(name = "lancerpick.wallet.topup-recovery.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class TopupRecoveryService {
  private final TopupRecoveryTransactions transactions;
  private final TossPaymentGateway gateway;

  public Result recover(long id, Instant staleBefore) {
    gateway.requireAvailable();
    var optional = transactions.claim(id, staleBefore);
    if (optional.isEmpty()) return Result.STALE;
    var claim = optional.get();
    try {
      var payment = gateway.lookup(claim.paymentKey());
      return transactions.apply(claim, payment);
    } catch (TossGatewayException unavailable) {
      return transactions.defer(claim, "PROVIDER_UNAVAILABLE");
    } catch (BusinessException invalidResult) {
      return transactions.defer(claim, "RESULT_REQUIRES_REVIEW");
    } catch (RuntimeException failedCommit) {
      log.warn("Top-up recovery deferred topupId={} code=CREDIT_PENDING", id);
      return transactions.defer(claim, "CREDIT_PENDING");
    }
  }
}
