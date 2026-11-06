package com.example.lancerpick.auth.mail;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * C3 메일 발송 인프라: AccountMailSender의 SMTP 구현(개발에서는 Mailpit).
 * MailInfrastructureConfiguration이 lancerpick.mail.enabled=true일 때만 Bean으로 등록한다.
 *
 * - enqueue는 크기가 정해진 큐에 넣고 바로 돌아온다. SMTP 통신은 전용 발송 스레드 하나에서만 한다.
 *   (요청 스레드에서 보내면 가입된 이메일만 응답이 느려져 가입 여부가 드러난다.)
 * - 큐가 가득 찼거나 종료 중이면 false. 그러면 Dispatcher가 FAILED(MAIL_QUEUE_FULL)로 기록한다.
 * - 발송이 끝나면 AccountMailDeliveryRecorder에 결과를 남긴다. 실패 코드는 예외 종류로만 정하고 메시지는 남기지 않는다.
 * - 로그에는 challengeId와 예외 클래스 이름만 남긴다. 수신자·본문(토큰)은 남기지 않는다.
 * - 메모리 큐라 서버 종료 때 남은 작업은 버려지고 PENDING으로 남는다. 사용자는 요청 제한 안에서 다시 요청한다.
 */
public class SmtpAccountMailSender implements AccountMailSender {
    private static final Logger log = LoggerFactory.getLogger(SmtpAccountMailSender.class);

    private final JavaMailSender mailSender;
    private final AccountMailDeliveryRecorder recorder;
    private final String from;
    private final ThreadPoolExecutor worker;

    public SmtpAccountMailSender(JavaMailSender mailSender, AccountMailDeliveryRecorder recorder, String from,
            int queueCapacity) {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("lancerpick.mail.from must not be blank when mail is enabled");
        }
        if (queueCapacity < 1) {
            throw new IllegalStateException("lancerpick.mail.queue-capacity must be at least 1");
        }
        this.mailSender = mailSender;
        this.recorder = recorder;
        this.from = from.trim();
        // 스레드 1개 + 고정 크기 큐. 가득 차면 AbortPolicy로 RejectedExecutionException → false.
        this.worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), runnable -> {
                    Thread thread = new Thread(runnable, "account-mail-sender");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public boolean enqueue(AccountMail mail) {
        try {
            worker.execute(() -> send(mail));
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    private void send(AccountMail mail) {
        String failure = null;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(mail.to());
            message.setSubject(mail.subject());
            message.setText(mail.body());
            mailSender.send(message);
        } catch (MailAuthenticationException e) {
            failure = "MAIL_AUTH_FAILED";
        } catch (MailParseException e) {
            failure = "MAIL_INVALID_MESSAGE";
        } catch (MailSendException e) {
            failure = "MAIL_SEND_FAILED";
        } catch (RuntimeException e) {
            failure = "MAIL_FAILED";
        }
        if (failure != null) {
            log.warn("account mail send failed challengeId={} code={}", mail.challengeId(), failure);
        }
        if (mail.tracked()) record(mail.challengeId(), failure);   // 안내 메일(challengeId 없음)은 기록하지 않는다.
    }

    private void record(long challengeId, String failure) {
        try {
            if (failure == null) {
                recorder.markSent(challengeId);
            } else {
                recorder.markFailed(challengeId, failure);
            }
        } catch (RuntimeException e) {
            // 기록 실패로 발송 스레드가 멈추지 않게 한다. 행은 PENDING으로 남는다.
            log.error("account mail delivery status not recorded challengeId={} type={}", challengeId,
                    e.getClass().getName());
        }
    }

    /**
     * 남은 큐는 버리고(PENDING으로 남음) 진행 중인 한 건에는 중단 신호를 보낸 뒤 최대 5초 기다린다.
     * SMTP 대기는 spring.mail.properties의 시간 제한(5초)으로도 끊긴다. 두 번 호출돼도 안전하다.
     */
    @PreDestroy
    public void shutdown() {
        worker.shutdownNow();
        try {
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("account mail sender did not stop within 5 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
