package com.example.lancerpick.wallet.repository;

import java.time.Instant;

/** Entity를 HTTP 응답에 직접 사용하지 않고 필요한 조회 컬럼만 가져오는 내부 projection. */
public interface WalletEntryView {
    Long getId();
    String getType();
    long getAmountDelta();
    long getBalanceAfter();
    Long getOrderId();
    // native Timestamp를 LocalDateTime으로 직접 변환하면 JVM 기본 시간대가 섞일 수 있다.
    // 공통 JDBC UTC 기준의 Instant로 받아 응답에서 UTC offset을 붙인다.
    Instant getCreatedAt();
}
