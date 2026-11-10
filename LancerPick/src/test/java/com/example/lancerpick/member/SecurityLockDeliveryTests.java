package com.example.lancerpick.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.example.lancerpick.member.dto.SecurityLockApplied;
import com.example.lancerpick.member.entity.MemberSanction;
import com.example.lancerpick.member.entity.SanctionKind;
import com.example.lancerpick.member.repository.MemberSanctionRepository;
import com.example.lancerpick.member.service.SecurityLockAfterCommit;
import com.example.lancerpick.member.service.SecurityLockDelivery;
import com.example.lancerpick.member.service.SecurityLockTimerStopper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class SecurityLockDeliveryTests {
    private SecurityLockDelivery service(
            MemberSanctionRepository repository, SecurityLockTimerStopper handler) {
        var beans = new StaticListableBeanFactory();
        beans.addBean("handler", handler);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new SecurityLockDelivery(
                repository,
                beans.getBeanProvider(SecurityLockTimerStopper.class),
                Clock.fixed(Instant.parse("2026-11-10T00:00:02Z"), ZoneOffset.UTC),
                manager);
    }

    private MemberSanction row() {
        return MemberSanction.apply(
                3,
                SanctionKind.SECURITY_LOCK,
                "private reason",
                2,
                UUID.randomUUID(),
                LocalDateTime.parse("2026-11-10T00:00:00"),
                LocalDateTime.parse("2026-11-10T00:00:01"));
    }

    @Test
    void aRowFailureDoesNotBlockTheRestAndAdvancesPastTheFailedId() {
        var repository = mock(MemberSanctionRepository.class);
        var handler = mock(SecurityLockTimerStopper.class);
        var delivery = service(repository, handler);
        when(repository.pendingSecurityLocksAfter(eq(0L), any())).thenReturn(List.of(11L, 12L));
        when(repository.findById(11L)).thenThrow(new IllegalArgumentException("private reason"));
        when(repository.findById(12L)).thenReturn(Optional.of(row()));
        var batch = delivery.retryPendingAfter(0, 2);
        assertThat(batch).isEqualTo(new SecurityLockDelivery.RetryBatch(1, 12));
        verify(handler).stop(argThat(event -> event.sanctionId() == 12));
        verify(repository).markTimerStopDelivered(12, LocalDateTime.parse("2026-11-10T00:00:02"));
        assertThatThrownBy(() -> delivery.retryPendingAfter(-1, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> delivery.retryPendingAfter(0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDatabaseOutageStopsTheBatchRatherThanWaitingOnEveryRemainingRow() {
        for (RuntimeException failure :
                List.of(
                        new DataAccessResourceFailureException("private details"),
                        new org.springframework.transaction.CannotCreateTransactionException(
                                "private details"))) {
            var repository = mock(MemberSanctionRepository.class);
            var delivery = service(repository, mock(SecurityLockTimerStopper.class));
            when(repository.pendingSecurityLocksAfter(eq(0L), any())).thenReturn(List.of(11L, 12L));
            when(repository.findById(11L)).thenThrow(failure);
            assertThatThrownBy(() -> delivery.retryPendingAfter(0, 2)).isSameAs(failure);
            verify(repository, never()).findById(12L);
        }
    }

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
