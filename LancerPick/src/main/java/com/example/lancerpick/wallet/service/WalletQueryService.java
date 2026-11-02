package com.example.lancerpick.wallet.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.wallet.dto.WalletBalanceResponse;
import com.example.lancerpick.wallet.dto.WalletEntryResponse;
import com.example.lancerpick.wallet.entity.WalletEntryType;
import com.example.lancerpick.wallet.repository.WalletEntryRepository;
import com.example.lancerpick.wallet.repository.WalletEntryView;
import com.example.lancerpick.wallet.repository.WalletRepository;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조회 준비용 내부 서비스. 향후 Controller는 인증 정보에서 얻은 회원 ID만 전달해야 한다.
 * 이 인자를 클라이언트의 쿼리·본문·경로에서 받는 공개 API는 만들지 않는다.
 */
@Service
@Profile("!demo")
@Transactional(readOnly = true)
public class WalletQueryService {
    private final WalletRepository wallets;
    private final WalletEntryRepository entries;

    public WalletQueryService(WalletRepository wallets, WalletEntryRepository entries) {
        this.wallets = wallets;
        this.entries = entries;
    }

    public WalletBalanceResponse getBalance(long authenticatedMemberId) {
        requireValidMemberId(authenticatedMemberId);
        var wallet = wallets.findById(authenticatedMemberId)
                .orElseThrow(WalletQueryService::walletNotFound);
        return WalletBalanceResponse.of(wallet.getBalance());
    }

    public PageResponse<WalletEntryResponse> getEntries(long authenticatedMemberId, int page, int size) {
        requireValidMemberId(authenticatedMemberId);
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "페이지 범위를 확인해 주세요.");
        }
        // 조회 대상 지갑이 없더라도 자동 생성하거나 적립금을 지급하지 않는다.
        if (!wallets.existsById(authenticatedMemberId)) throw walletNotFound();

        long total = entries.countByMemberId(authenticatedMemberId);
        int totalPages = Math.toIntExact(total / size + (total % size == 0 ? 0 : 1));
        long offset = (long) page * size;
        List<WalletEntryResponse> items = offset >= total ? List.of()
                : entries.findHistory(authenticatedMemberId, size, offset).stream()
                        .map(WalletQueryService::toResponse).toList();
        return new PageResponse<>(items, page, size, total, totalPages);
    }

    private static WalletEntryResponse toResponse(WalletEntryView entry) {
        return new WalletEntryResponse(entry.getId().toString(), WalletEntryType.valueOf(entry.getType()),
                entry.getAmountDelta(), entry.getBalanceAfter(),
                entry.getOrderId() == null ? null : entry.getOrderId().toString(),
                entry.getCreatedAt().atOffset(ZoneOffset.UTC));
    }

    private static void requireValidMemberId(long memberId) {
        if (memberId <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "회원 ID를 확인해 주세요.");
        }
    }

    private static BusinessException walletNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "지갑을 찾을 수 없습니다.");
    }
}
