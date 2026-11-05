package com.example.lancerpick.notification.dto;

import java.time.OffsetDateTime;

/** 문구는 화면이 type으로 정한다. orderId·productTitle은 주문과 무관한 알림이면 null이다. */
public record NotificationResponse(
    String id,
    String type,
    String orderId,
    String productTitle,
    boolean read,
    OffsetDateTime createdAt) {}
