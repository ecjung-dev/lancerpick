package com.example.lancerpick.member.service;

import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.member.dto.SecurityLockApplied;
import com.example.lancerpick.member.entity.SanctionKind;
import com.example.lancerpick.member.repository.MemberSanctionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * At-least-once internal delivery. Pending source rows survive missing handlers, crashes and
 * expiration.
 */
@Service
@Profile("!demo")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class SecurityLockDelivery {
    private static final Logger log = LoggerFactory.getLogger(SecurityLockDelivery.class);
    private final MemberSanctionRepository repository;
    private final ObjectProvider<SecurityLockTimerStopper> handlers;
    private final Clock clock;
    private final TransactionTemplate tx;

    public SecurityLockDelivery(
            MemberSanctionRepository repository,
            ObjectProvider<SecurityLockTimerStopper> handlers,
            Clock clock,
            PlatformTransactionManager manager) {
        this.repository = repository;
        this.handlers = handlers;
        this.clock = clock;
        this.tx = new TransactionTemplate(manager);
    }

    public boolean deliver(long id) {
        var handler = handlers.getIfAvailable();
        if (handler == null) {
            return false;
        }
        var row = repository.findById(id).orElse(null);
        if (row == null
                || row.getKind() != SanctionKind.SECURITY_LOCK
                || row.getTimerStopDeliveredAt() != null) {
            return false;
        }
        try {
            handler.stop(
                    new SecurityLockApplied(
                            id,
                            row.getAppliedBy(),
                            row.getMemberId(),
                            row.getReason(),
                            row.getStartsAt()));
            tx.executeWithoutResult(ignored -> {
                var current = repository.findById(id).orElseThrow();
                current.delivered(UtcTimes.nowMicros(clock));
                repository.flush();
            });
            return true;
        } catch (RuntimeException exception) {
            log.warn(
                    "Security lock timer delivery {} failed: {}",
                    id,
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    public int retryPending(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Invalid limit");
        }
        if (handlers.getIfAvailable() == null) {
            return 0;
        }
        int count = 0;
        for (long id : repository.pendingSecurityLocks(PageRequest.of(0, limit))) {
            if (deliver(id)) {
                count++;
            }
        }
        return count;
    }
}
