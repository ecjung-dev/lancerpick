package com.example.lancerpick.wallet.dto;

import com.example.lancerpick.wallet.entity.WalletEntryType;
import java.time.OffsetDateTime;

/** 원장 소유자·paymentId·내부 eventKey는 응답으로 노출하지 않는다. */
public record WalletEntryResponse(String id, WalletEntryType type, long amountDelta,
        long balanceAfter, String orderId, OffsetDateTime createdAt) {}
