package com.example.lancerpick.auth.mail;

/**
 * 계정 메일 한 통. challengeId는 발송 결과를 AccountMailDeliveryRecorder에 알릴 때 쓴다.
 * 토큰이 없어 account_challenges 행이 없는 안내 메일(예: 아이디 안내)은 {@link #untracked}로 만들고,
 * challengeId가 null이면 발송 결과를 DB에 기록하지 않는다(안전한 로그만 남김, 2026-11-06 결정).
 * body에는 일회용 링크(토큰 원문)가 들어 있을 수 있으므로 로그·DB·아웃박스에 남기지 않는다.
 * toString은 수신자·본문을 숨긴다(실수로 로그에 찍혀도 토큰이 새지 않게).
 */
public record AccountMail(Long challengeId, String to, String subject, String body) {
    /** 발송 결과를 기록하지 않는 안내 메일. */
    public static AccountMail untracked(String to, String subject, String body) {
        return new AccountMail(null, to, subject, body);
    }

    /** 발송 결과를 account_challenges.delivery_status에 기록하는 메일인지. */
    public boolean tracked() {
        return challengeId != null;
    }

    @Override
    public String toString() {
        return "AccountMail[challengeId=" + challengeId + ", to=***, subject=" + subject + ", body=***]";
    }
}
