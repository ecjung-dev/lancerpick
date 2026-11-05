package com.example.lancerpick.auth.mail;

/**
 * 계정 메일 발송 경계. 구현은 C3 메일 발송 인프라(Spring Mail·Mailpit, 정은찬·황준범 담당, 작업표 C3)가 제공한다.
 * 구현은 SmtpAccountMailSender이며 lancerpick.mail.enabled=true일 때만 등록된다(기본 꺼짐).
 * 등록되지 않으면 AccountMailDispatcher가 발송하지 않고 FAILED(MAIL_NOT_CONFIGURED)로 기록한다.
 *
 * 구현 계약(4인 설계 §4, 3인 설계 7.2):
 * - enqueue는 호출한 요청 스레드를 막지 않는다. 크기가 정해진 비동기 큐에 넣고 바로 돌아온다.
 *   SMTP 통신을 요청 스레드에서 하면 가입된 이메일만 응답이 느려져 가입 여부가 드러난다.
 * - 큐에 넣었으면 true, 가득 차서 넣지 못했으면 false. 예외를 던져도 요청 응답은 같은 202다.
 * - 실제 발송이 끝나면 AccountMailDeliveryRecorder.markSent / markFailed(안전한 오류 코드)를 호출한다.
 * - 메일 본문(토큰)을 로그·DB·거래 아웃박스(outbox_events)에 평문으로 남기지 않는다.
 * - 메모리 큐는 서버 종료 때 작업을 잃을 수 있다. 사용자는 요청 제한 안에서 다시 요청해 복구한다.
 */
public interface AccountMailSender {
    boolean enqueue(AccountMail mail);
}
