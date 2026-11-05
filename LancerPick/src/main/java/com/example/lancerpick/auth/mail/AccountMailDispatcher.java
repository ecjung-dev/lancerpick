package com.example.lancerpick.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 발급이 커밋된 계정 메일을 발송기(AccountMailSender, C3)에 넘긴다. 반드시 DB 트랜잭션 밖에서 호출한다.
 *
 * 어떤 경우에도 예외를 던지지 않는다. 메일을 받을 회원이 있을 때만 이 경로를 타므로,
 * 여기서 실패가 응답에 드러나면(500 등) 가입 여부가 드러난다. 결과는 delivery_status·안전한 로그로만 남긴다.
 * 로그에는 challengeId와 오류 코드만 남기고 이메일·토큰·예외 메시지는 남기지 않는다.
 */
@Component
@Profile("!demo")
public class AccountMailDispatcher {
    private static final Logger log = LoggerFactory.getLogger(AccountMailDispatcher.class);

    private final ObjectProvider<AccountMailSender> senders;
    private final AccountMailDeliveryRecorder recorder;

    public AccountMailDispatcher(ObjectProvider<AccountMailSender> senders, AccountMailDeliveryRecorder recorder) {
        this.senders = senders;
        this.recorder = recorder;
    }

    public void dispatch(AccountMail mail) {
        AccountMailSender sender = senders.getIfAvailable();
        if (sender == null) {
            // 메일 발송이 꺼져 있다(lancerpick.mail.enabled=false, 기본). 앱은 메일 서버 없이도 동작한다.
            fail(mail.challengeId(), "MAIL_NOT_CONFIGURED");
            return;
        }
        boolean queued;
        try {
            queued = sender.enqueue(mail);
        } catch (RuntimeException e) {
            log.warn("account mail enqueue failed challengeId={} type={}", mail.challengeId(), e.getClass().getName());
            fail(mail.challengeId(), "MAIL_ENQUEUE_FAILED");
            return;
        }
        if (!queued) {
            fail(mail.challengeId(), "MAIL_QUEUE_FULL");
        }
        // 큐에 넣었으면 PENDING 그대로 두고, 발송기가 끝난 뒤 markSent/markFailed를 호출한다.
    }

    private void fail(long challengeId, String code) {
        log.warn("account mail not sent challengeId={} code={}", challengeId, code);
        try {
            recorder.markFailed(challengeId, code);
        } catch (RuntimeException e) {
            log.error("account mail delivery status not recorded challengeId={} type={}", challengeId,
                    e.getClass().getName());
        }
    }
}
