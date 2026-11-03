package com.example.lancerpick.admin.dto;

import java.time.OffsetDateTime;

/** API 9절 운영 이력: id,adminId,action(HIDE·RESTORE),reason,createdAt. */
public record ModerationLogResponse(
        String id,
        String adminId,
        String action,
        String reason,
        OffsetDateTime createdAt
) {}
