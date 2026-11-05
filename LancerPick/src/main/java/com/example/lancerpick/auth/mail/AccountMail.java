package com.example.lancerpick.auth.mail;

/**
 * 계정 메일 한 통. challengeId는 발송 결과를 AccountMailDeliveryRecorder에 알릴 때 쓴다.
 * body에는 일회용 링크(토큰 원문)가 들어 있으므로 로그·DB·아웃박스에 남기지 않는다.
 * toString은 수신자·본문을 숨긴다(실수로 로그에 찍혀도 토큰이 새지 않게).
 */
public record AccountMail(long challengeId, String to, String subject, String body) {
    @Override
    public String toString() {
        return "AccountMail[challengeId=" + challengeId + ", to=***, subject=" + subject + ", body=***]";
    }
}
