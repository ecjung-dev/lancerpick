package com.example.lancerpick.wallet.repository;

import java.time.Instant;

/** Entity를 HTTP 응답에 직접 사용하지 않고 필요한 조회 컬럼만 가져오는 내부 projection. */
public interface WalletEntryView {
    Long getId();
    String getType();
    long getAmountDelta();
    long getBalanceAfter();
    Long getOrderId();
    // QueryDSL로 읽은 UTC LocalDateTime 원문을 구현체에서 Instant로 변환한다.
    // JVM 기본 시간대 대신 UTC를 명시하고 응답에서 UTC offset을 붙인다.
    Instant getCreatedAt();
}
