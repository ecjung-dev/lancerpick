package com.example.lancerpick.auth.entity;

/**
 * account_challenges.delivery_status (V5 CHECK, V12에 UNKNOWN 추가). PENDING: 발급만 됨(메일 발송기에 넘겼거나 넘기는
 * 중), SENT: 발송기가 보냈다고 알림, FAILED: 넘기지 못했거나 발송 실패. SENT는 "SMTP 서버가 받았다"는 뜻이지 받은편지함 도착을 뜻하지 않는다(3인 설계
 * 7.2).
 */
public enum ChallengeDeliveryStatus {
    PENDING,
    SENT,
    FAILED,
    /** 일정 시간 발송 결과가 기록되지 않음. 미발송을 뜻하지 않으며 늦은 결과로 확정할 수 있다. */
    UNKNOWN
}
