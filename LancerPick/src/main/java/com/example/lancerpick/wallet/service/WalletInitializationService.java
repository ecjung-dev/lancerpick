package com.example.lancerpick.wallet.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.wallet.entity.Wallet;
import com.example.lancerpick.wallet.entity.WalletEntry;
import com.example.lancerpick.wallet.repository.WalletEntryRepository;
import com.example.lancerpick.wallet.repository.WalletRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 회원가입 서비스가 회원 INSERT 후 같은 트랜잭션 안에서 호출하는 내부 기능. */
@Service
@Profile("!demo")
public class WalletInitializationService {
    private final WalletRepository wallets;
    private final WalletEntryRepository entries;
    private final Clock clock;

    public WalletInitializationService(WalletRepository wallets, WalletEntryRepository entries, Clock clock) {
        this.wallets = wallets;
        this.entries = entries;
        this.clock = clock;
    }

    /**
     * 독립 지급은 허용하지 않는다. 트랜잭션이 없으면 DB 처리 전에 거절한다.
     * 이미 정상 지급된 회원이면 잔액·시각·이력을 변경하지 않고 반환한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void initializeForNewMember(long memberId) {
        if (memberId <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "회원 ID를 확인해 주세요.");
        }
        wallets.lockMemberForInitialization(memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "RESOURCE_NOT_FOUND", "회원을 찾을 수 없습니다."));

        LocalDateTime grantedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);
        // INSERT IGNORE의 묵시적 값 보정을 피한다. 금액·ID·시각은 검증한 서버 값만 사용한다.
        if (grantedAt.getYear() < 1000 || grantedAt.getYear() > 9999) {
            throw new IllegalStateException("Clock is outside the MySQL DATETIME range");
        }
        Wallet initialWallet = Wallet.withInitialGrant(memberId, grantedAt);
        int inserted = wallets.insertInitialWalletIfAbsent(initialWallet.getMemberId(),
                initialWallet.getBalance(), initialWallet.getUpdatedAt().toInstant(ZoneOffset.UTC));
        if (inserted == 0) {
            var existingWallet = wallets.findByMemberIdForUpdate(memberId);
            var grant = entries.findByEventKeyForUpdate(WalletEntry.initialGrantKey(memberId));
            if (existingWallet.isEmpty() || grant.isEmpty() || !grant.get().isInitialGrantFor(memberId)) {
                // 불완전한 지갑을 자동 보정·재지급하면 금액 근거가 달라진다.
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "DATA_INTEGRITY_ERROR", "적립금 기록을 확인해야 합니다.");
            }
            return;
        }
        if (inserted != 1) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "DATA_INTEGRITY_ERROR", "적립금 생성 결과를 확인해야 합니다.");
        }
        // 지갑 INSERT는 실행됐지만 커밋하지 않았다. 원장 실패는 지갑·회원까지 함께 롤백한다.
        entries.saveAndFlush(WalletEntry.initialGrant(memberId, grantedAt));
        // 원장 저장이 실패하면 예외를 전파하여 호출자의 회원 생성까지 함께 롤백한다.
    }
}
