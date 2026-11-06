package com.example.lancerpick.auth;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.lancerpick.auth.mail.AccountMail;
import com.example.lancerpick.auth.mail.AccountMailDeliveryRecorder;
import com.example.lancerpick.auth.mail.AccountMailDispatcher;
import com.example.lancerpick.auth.mail.AccountMailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** DB 없이 확인: C3 메일 발송기가 없는 기본 상태에서도 예외 없이 FAILED(MAIL_NOT_CONFIGURED)만 기록한다. */
class AccountMailDispatcherTests {
    private static final AccountMail MAIL = new AccountMail(7L, "a@example.com", "subject", "body");

    @Test
    void withoutAMailSenderTheMailIsRecordedAsNotConfigured() {
        AccountMailDeliveryRecorder recorder = mock(AccountMailDeliveryRecorder.class);
        AccountMailDispatcher dispatcher = new AccountMailDispatcher(
                new StaticListableBeanFactory().getBeanProvider(AccountMailSender.class), recorder);

        dispatcher.dispatch(MAIL);

        verify(recorder).markFailed(7L, "MAIL_NOT_CONFIGURED");
    }

    @Test
    void recorderFailureDoesNotEscape() {
        AccountMailDeliveryRecorder recorder = mock(AccountMailDeliveryRecorder.class);
        doThrow(new IllegalStateException("db down")).when(recorder).markFailed(7L, "MAIL_NOT_CONFIGURED");
        AccountMailDispatcher dispatcher = new AccountMailDispatcher(
                new StaticListableBeanFactory().getBeanProvider(AccountMailSender.class), recorder);

        dispatcher.dispatch(MAIL);   // 예외가 밖으로 나오면 가입된 이메일만 500이 되어 가입 여부가 드러난다

        verify(recorder).markFailed(7L, "MAIL_NOT_CONFIGURED");
    }

    @Test
    void untrackedMailIsNotRecordedEvenWhenItCannotBeSent() {
        AccountMailDeliveryRecorder recorder = mock(AccountMailDeliveryRecorder.class);
        AccountMailDispatcher dispatcher = new AccountMailDispatcher(
                new StaticListableBeanFactory().getBeanProvider(AccountMailSender.class), recorder);

        dispatcher.dispatch(AccountMail.untracked("a@example.com", "subject", "body"));

        verifyNoInteractions(recorder);
    }

    @Test
    void untrackedMailFullQueueIsNotRecorded() {
        AccountMailDeliveryRecorder recorder = mock(AccountMailDeliveryRecorder.class);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("sender", (AccountMailSender) mail -> false);
        AccountMailDispatcher dispatcher = new AccountMailDispatcher(beans.getBeanProvider(AccountMailSender.class),
                recorder);

        dispatcher.dispatch(AccountMail.untracked("a@example.com", "subject", "body"));

        verifyNoInteractions(recorder);
    }
}
