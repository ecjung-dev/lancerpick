package com.example.lancerpick.member.service;

import com.example.lancerpick.member.dto.SecurityLockApplied;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Profile("!demo")
public class SecurityLockAfterCommit {
    private static final Logger log = LoggerFactory.getLogger(SecurityLockAfterCommit.class);
    private final SecurityLockDelivery delivery;

    public SecurityLockAfterCommit(SecurityLockDelivery delivery) {
        this.delivery = delivery;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplied(SecurityLockApplied event) {
        try {
            delivery.deliver(event.sanctionId());
        } catch (RuntimeException failure) {
            log.warn(
                    "Security lock {} delivery deferred: {}",
                    event.sanctionId(),
                    failure.getClass().getSimpleName());
        }
    }
}
