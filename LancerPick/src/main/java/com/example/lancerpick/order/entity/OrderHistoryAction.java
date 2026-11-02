package com.example.lancerpick.order.entity;

/** 주문 상태가 아니라 이력을 발생시킨 행동을 나타낸다. */
public enum OrderHistoryAction {
    CREATED,
    ACCEPTED,
    REJECTED,
    PAID,
    DELIVERED,
    REVISION_REQUESTED,
    CANCELLATION_REQUESTED,
    CANCELLATION_APPROVED,
    CANCELLATION_REJECTED,
    CANCELLATION_WITHDRAWN,
    CANCELLED,
    COMPLETED
}