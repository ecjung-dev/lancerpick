package com.example.lancerpick.wallet.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.wallet.dto.WalletTopupResponse;
import com.example.lancerpick.wallet.entity.WalletTopup;
import com.example.lancerpick.wallet.repository.WalletRepository;
import com.example.lancerpick.wallet.repository.WalletTopupRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 충전 신청·조회만 담당한다. 외부 승인·원장 생성·잔액 변경은 하지 않는다. */
@Service
@Profile("!demo")
public class WalletTopupService {
    private final WalletRepository wallets;
    private final WalletTopupRepository topups;
    private final Clock clock;

    public WalletTopupService(WalletRepository wallets, WalletTopupRepository topups, Clock clock) {
        this.wallets = wallets;
        this.topups = topups;
        this.clock = clock;
    }

    @Transactional
    public WalletTopupResponse create(long authenticatedMemberId, long amount) {
        requirePositiveId(authenticatedMemberId);
        if (amount < 1 || amount > WalletTopup.MAX_AMOUNT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "충전 금액은 1원부터 100만원까지 입력해 주세요.");
        }
        if (!wallets.existsById(authenticatedMemberId)) throw notFound();
        var topup = WalletTopup.request(authenticatedMemberId, "topup_" + UUID.randomUUID(),
                amount, UUID.randomUUID(), clock.instant());
        return WalletTopupResponse.from(topups.saveAndFlush(topup));
    }

    @Transactional(readOnly = true)
    public WalletTopupResponse get(long authenticatedMemberId, long topupId) {
        requirePositiveId(authenticatedMemberId);
        requirePositiveId(topupId);
        // 타인 건과 존재하지 않는 건을 동일하게 404 처리한다.
        return WalletTopupResponse.from(topups.findByIdAndMemberId(topupId, authenticatedMemberId)
                .orElseThrow(WalletTopupService::notFound));
    }

    private static void requirePositiveId(long id) {
        if (id <= 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "식별자를 확인해 주세요.");
    }

    private static BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "충전 대상 또는 신청을 찾을 수 없습니다.");
    }
}
