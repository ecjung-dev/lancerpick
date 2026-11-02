package com.example.lancerpick.wallet.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.wallet.dto.WalletTopupResponse;
import com.example.lancerpick.wallet.entity.WalletEntryType;
import com.example.lancerpick.wallet.entity.WalletTopup;
import com.example.lancerpick.wallet.entity.WalletTopupStatus;
import com.example.lancerpick.wallet.gateway.TossPaymentGateway.Payment;
import com.example.lancerpick.wallet.repository.WalletEntryRepository;
import com.example.lancerpick.wallet.repository.WalletRepository;
import com.example.lancerpick.wallet.repository.WalletTopupRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 외부 호출 없이 짧은 DB 트랜잭션만 실행한다. 잠금 순서는 충전 행→지갑이다. */
@Service
@Profile("!demo")
@Transactional
public class TopupApprovalTransactions {
    private final WalletTopupRepository topups;
    private final WalletRepository wallets;
    private final WalletEntryRepository entries;
    private final Clock clock;

    public TopupApprovalTransactions(WalletTopupRepository topups, WalletRepository wallets,
            WalletEntryRepository entries, Clock clock) {
        this.topups = topups; this.wallets = wallets; this.entries = entries; this.clock = clock;
    }

    public record Attempt(long memberId, long topupId, String paymentKey, String orderId,
            long amount, String requestKey, boolean execute) {}

    public Attempt prepare(long memberId, long id, String paymentKey, String orderId, long amount) {
        WalletTopup topup = owned(memberId, id);
        if (!topup.getTossOrderId().equals(orderId) || topup.getAmount() != amount) {
            throw new BusinessException(HttpStatus.CONFLICT, "TOPUP_TERMS_MISMATCH", "저장된 충전 주문번호와 금액을 확인해 주세요.");
        }
        if (paymentKey == null || !paymentKey.matches("[\\x21-\\x7E]{1,200}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "결제 식별자를 확인해 주세요.");
        }
        if (topup.getPaymentKey() != null && !topup.getPaymentKey().equals(paymentKey)) {
            throw new BusinessException(HttpStatus.CONFLICT, "TOPUP_PAYMENT_KEY_CONFLICT", "기존 충전 요청과 결제 정보가 다릅니다.");
        }
        if (topup.getStatus() == WalletTopupStatus.FAILED) {
            throw new BusinessException(HttpStatus.CONFLICT, "TOPUP_STATE_CONFLICT", "종료된 충전 신청입니다.");
        }
        if (topup.getStatus() == WalletTopupStatus.COMPLETED) verifyCompleted(topup);
        boolean execute = topup.getStatus() == WalletTopupStatus.READY;
        if (execute) topup.startProcessing(paymentKey, clock.instant());
        topups.flush(); // 식별자 고유 제약 실패도 외부 호출 전에 확정한다.
        return attempt(topup, execute);
    }

    public Attempt recoveryAttempt(long memberId, long id) {
        var topup = owned(memberId, id);
        boolean active = topup.getStatus() == WalletTopupStatus.PROCESSING || topup.getStatus() == WalletTopupStatus.UNKNOWN;
        return attempt(topup, active);
    }

    public WalletTopupResponse unknown(long memberId, long id, String safeCode) {
        var topup = owned(memberId, id);
        topup.markUnknown(safeCode, clock.instant());
        return WalletTopupResponse.from(topup);
    }

    public WalletTopupResponse apply(long memberId, long id, Payment payment) {
        var topup = owned(memberId, id);
        if (topup.getStatus() == WalletTopupStatus.COMPLETED) {
            verifyCompleted(topup);
            return WalletTopupResponse.from(topup);
        }
        if (topup.getStatus() != WalletTopupStatus.PROCESSING && topup.getStatus() != WalletTopupStatus.UNKNOWN) {
            throw new BusinessException(HttpStatus.CONFLICT, "TOPUP_STATE_CONFLICT", "충전 처리 상태를 확인해 주세요.");
        }
        if (payment == null || !Objects.equals(payment.paymentKey(), topup.getPaymentKey())
                || !Objects.equals(payment.orderId(), topup.getTossOrderId())
                || payment.totalAmount() != topup.getAmount() || !"KRW".equals(payment.currency()) || !topup.isTest()) {
            throw mismatch();
        }
        if (!"DONE".equals(payment.status())) {
            if (Set.of("ABORTED", "EXPIRED", "CANCELED").contains(Objects.toString(payment.status(), ""))) {
                topup.markFailed(clock.instant());
            } else {
                topup.markUnknown("PROVIDER_NOT_COMPLETED", clock.instant());
            }
            return WalletTopupResponse.from(topup);
        }
        if (payment.balanceAmount() != topup.getAmount() || payment.approvedAt() == null
                || payment.approvedAt().toInstant().isBefore(topup.getCreatedAt())) throw mismatch();

        var wallet = wallets.findByMemberIdForUpdate(memberId).orElseThrow(TopupApprovalTransactions::integrity);
        String eventKey = "TOPUP:" + topup.getId();
        // 없는 원장의 FOR UPDATE 간격 잠금을 피한다. 충전/지갑 잠금과 최종 UNIQUE가 중복을 막는다.
        if (entries.existsByTopupId(id) || entries.existsByEventKey(eventKey)) throw integrity();
        long balance = Math.addExact(wallet.getBalance(), topup.getAmount());
        topup.complete(payment.approvedAt().toInstant(), clock.instant());
        if (wallets.updateTopupBalance(memberId, balance, topup.getCompletedAt()) != 1) throw integrity();
        if (entries.insertTopup(memberId, id, topup.getAmount(), balance, eventKey, topup.getCompletedAt()) != 1) throw integrity();
        topups.flush();
        return WalletTopupResponse.from(topup);
    }

    private void verifyCompleted(WalletTopup topup) {
        var entry = entries.findByEventKeyForUpdate("TOPUP:" + topup.getId()).orElseThrow(TopupApprovalTransactions::integrity);
        if (!Objects.equals(entry.getMemberId(), topup.getMemberId())
                || !Objects.equals(entry.getTopupId(), topup.getId()) || entry.getPaymentId() != null
                || entry.getType() != WalletEntryType.TOPUP || entry.getAmountDelta() != topup.getAmount()) throw integrity();
    }

    private WalletTopup owned(long memberId, long id) {
        return topups.findOwnedByIdForUpdate(id, memberId).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "충전 신청을 찾을 수 없습니다."));
    }
    private static Attempt attempt(WalletTopup topup, boolean execute) {
        return new Attempt(topup.getMemberId(), topup.getId(), topup.getPaymentKey(), topup.getTossOrderId(),
                topup.getAmount(), topup.getApprovalRequestKey(), execute);
    }
    private static BusinessException mismatch() {
        return new BusinessException(HttpStatus.BAD_GATEWAY, "TOPUP_PAYMENT_MISMATCH", "외부 결제 결과를 확인해야 합니다.");
    }
    private static BusinessException integrity() {
        return new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY_ERROR", "충전 지급 기록을 확인해야 합니다.");
    }
}
