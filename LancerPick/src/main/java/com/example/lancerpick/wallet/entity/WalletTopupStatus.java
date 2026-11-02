package com.example.lancerpick.wallet.entity;

/** 내부 지급 처리 상태. 토스 Payment.status를 그대로 저장하는 enum이 아니다. */
public enum WalletTopupStatus {
    READY, PROCESSING, UNKNOWN, COMPLETED, FAILED
}
