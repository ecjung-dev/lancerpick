package com.example.lancerpick.wallet.controller;

import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.wallet.dto.WalletBalanceResponse;
import com.example.lancerpick.wallet.dto.WalletEntryResponse;
import com.example.lancerpick.wallet.service.WalletQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 실제 로그인 구현은 별도다. 인증 principal 계약을 충족한 본인 지갑만 조회한다. */
@RestController
@Profile("!demo")
@RequestMapping("/api/me/wallet")
public class WalletController {
    private final WalletQueryService wallets;
    private final CurrentMemberId currentMemberId;

    public WalletController(WalletQueryService wallets, CurrentMemberId currentMemberId) {
        this.wallets = wallets;
        this.currentMemberId = currentMemberId;
    }

    @GetMapping
    public ResponseEntity<WalletBalanceResponse> balance(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(wallets.getBalance(currentMemberId.require(authentication)));
    }

    @GetMapping("/entries")
    public ResponseEntity<PageResponse<WalletEntryResponse>> entries(Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(wallets.getEntries(currentMemberId.require(authentication), page, size));
    }
}
