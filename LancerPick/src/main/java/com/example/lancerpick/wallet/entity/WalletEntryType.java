package com.example.lancerpick.wallet.entity;

/** V1 기본 코드와 V2 테스트 충전 코드. 충전·결제·환불 쓰기 서비스는 별도다. */
public enum WalletEntryType {
    INITIAL_GRANT, PAYMENT, REFUND, TOPUP, SELLER_PAYOUT
}
