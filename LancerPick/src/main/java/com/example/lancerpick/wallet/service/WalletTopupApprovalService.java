package com.example.lancerpick.wallet.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.wallet.dto.WalletTopupResponse;
import com.example.lancerpick.wallet.entity.WalletTopupStatus;
import com.example.lancerpick.wallet.gateway.TossGatewayException;
import com.example.lancerpick.wallet.gateway.TossPaymentGateway;
import com.example.lancerpick.wallet.gateway.TossPaymentGateway.Payment;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 외부 호출을 조율한다. 상위 DB 트랜잭션 안에서 호출하지 못하게 NEVER를 적용한다. */
@Service
@Profile("!demo")
@Transactional(propagation = Propagation.NEVER)
public class WalletTopupApprovalService {
    private static final Logger log = LoggerFactory.getLogger(WalletTopupApprovalService.class);
    private final WalletTopupService topups;
    private final TopupApprovalTransactions transactions;
    private final TossPaymentGateway gateway;

    public WalletTopupApprovalService(WalletTopupService topups, TopupApprovalTransactions transactions, TossPaymentGateway gateway) {
        this.topups = topups; this.transactions = transactions; this.gateway = gateway;
    }

    public WalletTopupResponse confirm(long memberId, long id, String paymentKey, String orderId, long amount) {
        var current = topups.get(memberId, id);
        if (current.status() == WalletTopupStatus.READY) gateway.requireAvailable();
        TopupApprovalTransactions.Attempt attempt;
        try {
            attempt = transactions.prepare(memberId, id, paymentKey, orderId, amount);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, "TOPUP_PAYMENT_KEY_CONFLICT", "기존 결제 정보와 충돌합니다.");
        }
        if (!attempt.execute()) return topups.get(memberId, id);
        Payment payment;
        try {
            payment = gateway.confirm(attempt.paymentKey(), attempt.orderId(), attempt.amount(), attempt.requestKey());
        } catch (TossGatewayException exception) {
            return transactions.unknown(memberId, id, "PROVIDER_UNAVAILABLE");
        }
        return apply(memberId, id, payment);
    }

    public WalletTopupResponse reconcile(long memberId, long id) {
        topups.get(memberId, id); // 외부 조회 전 소유권부터 확인한다.
        var attempt = transactions.recoveryAttempt(memberId, id);
        if (!attempt.execute()) return topups.get(memberId, id);
        gateway.requireAvailable();
        try {
            return apply(memberId, id, gateway.lookup(attempt.paymentKey()));
        } catch (TossGatewayException exception) {
            return transactions.unknown(memberId, id, "PROVIDER_UNAVAILABLE");
        }
    }

    private WalletTopupResponse apply(long memberId, long id, Payment payment) {
        try {
            return transactions.apply(memberId, id, payment);
        } catch (BusinessException exception) {
            transactions.unknown(memberId, id, "RESULT_REQUIRES_REVIEW");
            throw exception;
        } catch (RuntimeException exception) {
            // 외부 승인 후 로컬 커밋 실패는 실패 결제로 단정하지 않는다. rollback 후 별도 기록.
            log.warn("Top-up credit pending topupId={} failureType={}", id, exception.getClass().getSimpleName());
            return transactions.unknown(memberId, id, "CREDIT_PENDING");
        }
    }
}
