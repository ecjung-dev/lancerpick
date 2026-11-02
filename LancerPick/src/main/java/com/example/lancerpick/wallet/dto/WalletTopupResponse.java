package com.example.lancerpick.wallet.dto;

import com.example.lancerpick.wallet.entity.WalletTopup;
import com.example.lancerpick.wallet.entity.WalletTopupStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 내부 승인 멱등키·paymentKey·오류 원문은 노출하지 않는다. */
public record WalletTopupResponse(String id, String tossOrderId, long amount, String currency,
        boolean isTest, WalletTopupStatus status, OffsetDateTime createdAt) {
    public static WalletTopupResponse from(WalletTopup topup) {
        return new WalletTopupResponse(topup.getId().toString(), topup.getTossOrderId(),
                topup.getAmount(), topup.getCurrency(), topup.isTest(), topup.getStatus(),
                topup.getCreatedAt().atOffset(ZoneOffset.UTC));
    }
}
