package com.example.lancerpick.wallet.controller;

import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.wallet.dto.CreateWalletTopupRequest;
import com.example.lancerpick.wallet.dto.ConfirmWalletTopupRequest;
import com.example.lancerpick.wallet.entity.WalletTopupStatus;
import com.example.lancerpick.wallet.dto.WalletTopupResponse;
import com.example.lancerpick.wallet.service.WalletTopupService;
import com.example.lancerpick.wallet.service.WalletTopupApprovalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!demo")
@RequestMapping("/api/me/wallet/topups")
public class WalletTopupController {
    private final WalletTopupService topups;
    private final CurrentMemberId currentMemberId;
    private final WalletTopupApprovalService approvals;

    public WalletTopupController(WalletTopupService topups, CurrentMemberId currentMemberId, WalletTopupApprovalService approvals) {
        this.topups = topups;
        this.currentMemberId = currentMemberId;
        this.approvals = approvals;
    }

    @PostMapping
    public ResponseEntity<WalletTopupResponse> create(Authentication authentication,
            @Valid @RequestBody CreateWalletTopupRequest request) {
        var result = topups.create(currentMemberId.require(authentication), request.amount().longValueExact());
        return ResponseEntity.created(URI.create("/api/me/wallet/topups/" + result.id()))
                .cacheControl(CacheControl.noStore()).body(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WalletTopupResponse> get(Authentication authentication, @PathVariable @Min(1) long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(topups.get(currentMemberId.require(authentication), id));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<WalletTopupResponse> confirm(Authentication authentication, @PathVariable @Min(1) long id,
            @Valid @RequestBody ConfirmWalletTopupRequest request) {
        return approvalResponse(approvals.confirm(currentMemberId.require(authentication), id,
                request.paymentKey(), request.orderId(), request.amount().longValueExact()));
    }

    @PostMapping("/{id}/reconcile")
    public ResponseEntity<WalletTopupResponse> reconcile(Authentication authentication, @PathVariable @Min(1) long id) {
        return approvalResponse(approvals.reconcile(currentMemberId.require(authentication), id));
    }

    private static ResponseEntity<WalletTopupResponse> approvalResponse(WalletTopupResponse result) {
        boolean pending = result.status() == WalletTopupStatus.PROCESSING || result.status() == WalletTopupStatus.UNKNOWN;
        return ResponseEntity.status(pending ? 202 : 200).cacheControl(CacheControl.noStore()).body(result);
    }
}
