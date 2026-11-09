package com.example.lancerpick.member.service;

import com.example.lancerpick.member.dto.SecurityLockApplied;

/**
 * Implemented by the timer owner. Must be idempotent and return only after all affected orders are
 * handled.
 */
public interface SecurityLockTimerStopper {
    void stop(SecurityLockApplied lock);
}
