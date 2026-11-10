package com.example.lancerpick.admin.dto;

import com.example.lancerpick.admin.entity.AdminAction;
import com.example.lancerpick.admin.entity.AdminActionLog;
import com.example.lancerpick.admin.entity.AdminActionStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 금액·결제 키·파일 정보·예외 원문을 포함하지 않는 내부 연결 응답. */
public record AdminActionReceipt(
    String id,
    String adminId,
    AdminAction action,
    String targetType,
    String targetId,
    String orderId,
    String reason,
    String requestKey,
    AdminActionStatus status,
    String resultCode,
    OffsetDateTime requestedAt,
    OffsetDateTime leaseUntil,
    OffsetDateTime completedAt) {
  public static AdminActionReceipt from(AdminActionLog log) {
    return new AdminActionReceipt(
        log.getId().toString(),
        Long.toString(log.getAdminId()),
        log.getAction(),
        log.getTargetType(),
        Long.toString(log.getTargetId()),
        log.getOrderId() == null ? null : log.getOrderId().toString(),
        log.getReason(),
        log.getRequestKey(),
        log.getStatus(),
        log.getResultCode(),
        log.getRequestedAt().atOffset(ZoneOffset.UTC),
        log.getLeaseUntil().atOffset(ZoneOffset.UTC),
        log.getCompletedAt() == null ? null : log.getCompletedAt().atOffset(ZoneOffset.UTC));
  }
}
