package com.example.lancerpick.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

import com.example.lancerpick.member.dto.SecurityLockApplied;
import com.example.lancerpick.member.repository.MemberSanctionRepository;
import com.example.lancerpick.member.service.SecurityLockAfterCommit;
import com.example.lancerpick.member.service.SecurityLockDelivery;
import com.example.lancerpick.member.service.SecurityLockTimerStopper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.LocalDateTime;

class SecurityLockDeliveryTests {
    @Test
    void missingTimerAdapterKeepsPendingHistoryUntouched() {
        var repository = mock(MemberSanctionRepository.class);
        var service =
                new SecurityLockDelivery(
                        repository,
                        new StaticListableBeanFactory()
                                .getBeanProvider(SecurityLockTimerStopper.class),
                        Clock.systemUTC(),
                        mock(PlatformTransactionManager.class));
        assertThat(service.deliver(1)).isFalse();
        assertThat(service.retryPending(10)).isZero();
        verifyNoInteractions(repository);
    }

    @Test
    void deliveryDatabaseFailureNeverChangesAlreadyCommittedCommandOutcome() {
        var delivery = mock(SecurityLockDelivery.class);
        when(delivery.deliver(1)).thenThrow(new IllegalStateException("injected failure"));
        assertThatCode(() ->
                        new SecurityLockAfterCommit(delivery)
                                .onApplied(
                                        new SecurityLockApplied(
                                                1, 2, 3, "reason", LocalDateTime.now())))
                .doesNotThrowAnyException();
    }
}
