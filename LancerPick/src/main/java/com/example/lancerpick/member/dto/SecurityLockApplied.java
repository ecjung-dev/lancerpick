package com.example.lancerpick.member.dto;

import java.time.LocalDateTime;

/** No credentials. Handler must only stop timers affected by this lock's appliedAt. */
public record SecurityLockApplied(
        long sanctionId, long adminId, long memberId, String reason, LocalDateTime appliedAt) {}
