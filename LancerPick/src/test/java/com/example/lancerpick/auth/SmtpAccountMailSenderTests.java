package com.example.lancerpick.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.example.lancerpick.auth.mail.AccountMail;
import com.example.lancerpick.auth.mail.AccountMailDeliveryRecorder;
import com.example.lancerpick.auth.mail.SmtpAccountMailSender;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** DB·SMTP 없이 확인: C3 발송기는 요청 스레드를 막지 않고, 발송 결과를 안전한 코드로만 기록한다. */
class SmtpAccountMailSenderTests {
    private static final String FROM = "LancerPick <no-reply@lancerpick.local>";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final AccountMailDeliveryRecorder recorder = mock(AccountMailDeliveryRecorder.class);
    private SmtpAccountMailSender sender;

    @AfterEach
    void stop() {
        if (sender != null) sender.shutdown();
    }

    private static AccountMail mail(long id) {
        return new AccountMail(id, "user@example.com", "[랜서픽] 비밀번호 재설정 안내", "링크 #token=secret");
    }

    @Test
    void sendsOnTheMailThreadAndRecordsSent() {
        AtomicReference<String> sendingThread = new AtomicReference<>();
        doAnswer(inv -> {
            sendingThread.set(Thread.currentThread().getName());
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));
        sender = new SmtpAccountMailSender(mailSender, recorder, FROM, 10);

        assertThat(sender.enqueue(mail(1L))).isTrue();

        verify(recorder, timeout(2000)).markSent(1L);
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getFrom()).isEqualTo(FROM);
        assertThat(sent.getValue().getTo()).containsExactly("user@example.com");
        assertThat(sent.getValue().getSubject()).isEqualTo("[랜서픽] 비밀번호 재설정 안내");
        assertThat(sent.getValue().getText()).isEqualTo("링크 #token=secret");
        assertThat(sendingThread.get()).isEqualTo("account-mail-sender");
    }

    @Test
    void enqueueReturnsWithoutWaitingForSmtp() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(inv -> {
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));
        sender = new SmtpAccountMailSender(mailSender, recorder, FROM, 10);

        long started = System.nanoTime();
        assertThat(sender.enqueue(mail(1L))).isTrue();
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(1000);

        verify(recorder, never()).markSent(anyLong());
        release.countDown();
        verify(recorder, timeout(2000)).markSent(1L);
    }

    @Test
    void returnsFalseWhenTheQueueIsFull() throws Exception {
        CountDownLatch inSend = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(inv -> {
            inSend.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));
        sender = new SmtpAccountMailSender(mailSender, recorder, FROM, 1);

        assertThat(sender.enqueue(mail(1L))).isTrue();   // 발송 스레드가 잡음
        assertThat(inSend.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(sender.enqueue(mail(2L))).isTrue();   // 큐 1칸
        assertThat(sender.enqueue(mail(3L))).isFalse();  // 가득 참 → Dispatcher가 MAIL_QUEUE_FULL 기록

        release.countDown();
        verify(recorder, timeout(2000)).markSent(1L);
        verify(recorder, timeout(2000)).markSent(2L);
        verify(recorder, never()).markSent(3L);
    }

    @Test
    void untrackedMailIsSentWithoutRecordingTheResult() {
        sender = new SmtpAccountMailSender(mailSender, recorder, FROM, 10);

        assertThat(sender.enqueue(AccountMail.untracked("user@example.com", "[랜서픽] 아이디 안내", "본문"))).isTrue();

        verify(mailSender, timeout(2000)).send(any(SimpleMailMessage.class));
        // 발송 스레드가 기록 단계까지 끝나도록 같은 큐에 기록 대상 메일을 하나 더 넣고 그 결과를 기다린다.
        sender.enqueue(mail(9L));
        verify(recorder, timeout(2000)).markSent(9L);
        verify(recorder).markSent(anyLong());   // 기록 대상 메일 1건만 기록됨
        verify(recorder, never()).markFailed(anyLong(), anyString());
    }

    @Test
    void untrackedMailFailureIsNotRecorded() {
        doThrow(new MailSendException("connection refused")).doNothing()
                .when(mailSender).send(any(SimpleMailMessage.class));
        sender = new SmtpAccountMailSender(mailSender, recorder, FROM, 10);

        sender.enqueue(AccountMail.untracked("user@example.com", "[랜서픽] 아이디 안내", "본문"));
        sender.enqueue(mail(9L));

        verify(recorder, timeout(2000)).markSent(9L);
        verify(recorder, never()).markFailed(anyLong(), anyString());
    }

    @Test
    void smtpFailuresAreRecordedAsSafeCodes() {
        doThrow(new MailAuthenticationException("bad password for user@example.com"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        sender = new SmtpAccountMailSender(mailSender, recorder, FROM, 10);
        sender.enqueue(mail(1L));
        verify(recorder, timeout(2000)).markFailed(1L, "MAIL_AUTH_FAILED");
    }

    @Test
    void sendFailureAndUnexpectedErrorsAreRecordedAndTheWorkerKeepsRunning() {
        doThrow(new MailSendException("connection refused"))
                .doThrow(new IllegalStateException("boom"))
                .doNothing()
                .when(mailSender).send(any(SimpleMailMessage.class));
        sender = new SmtpAccountMailSender(mailSender, recorder, FROM, 10);

        sender.enqueue(mail(1L));
        sender.enqueue(mail(2L));
        sender.enqueue(mail(3L));

        verify(recorder, timeout(2000)).markFailed(1L, "MAIL_SEND_FAILED");
        verify(recorder, timeout(2000)).markFailed(2L, "MAIL_FAILED");
        verify(recorder, timeout(2000)).markSent(3L);
    }

    @Test
    void recorderFailureDoesNotStopTheWorker() {
        doThrow(new IllegalStateException("db down")).when(recorder).markSent(1L);
        sender = new SmtpAccountMailSender(mailSender, recorder, FROM, 10);

        sender.enqueue(mail(1L));
        sender.enqueue(mail(2L));

        verify(recorder, timeout(2000)).markSent(2L);
    }

    @Test
    void afterShutdownEnqueueReturnsFalse() {
        sender = new SmtpAccountMailSender(mailSender, recorder, FROM, 10);
        sender.shutdown();

        assertThat(sender.enqueue(mail(1L))).isFalse();
        verify(recorder, never()).markFailed(anyLong(), anyString());
    }

    @Test
    void rejectsBlankFromAndEmptyQueue() {
        assertThatThrownBy(() -> new SmtpAccountMailSender(mailSender, recorder, " ", 10))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SmtpAccountMailSender(mailSender, recorder, FROM, 0))
                .isInstanceOf(IllegalStateException.class);
    }
}
