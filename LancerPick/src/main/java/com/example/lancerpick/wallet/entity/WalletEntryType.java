package com.example.lancerpick.wallet.entity;

/** 기존 V1의 허용 코드. 결제·환불 쓰기 경로는 후속 구현한다. */
public enum WalletEntryType {
    INITIAL_GRANT, PAYMENT, REFUND
}
