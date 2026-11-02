package com.example.lancerpick.order.entity;

public enum OrderStatus {
    REQUESTED,              // 주문 요청, 판매자 수락 대기
    AWAITING_PAYMENT,       // 판매자 수락 완료, 구매자 결제 대기
    IN_PROGRESS,            // 결제 완료, 작업 진행 중
    AWAITING_CONFIRMATION,  // 납품 완료, 구매자 확인 대기
    REVISION_IN_PROGRESS,   // 수정 요청에 따른 작업 진행 중
    COMPLETED,              // 구매 확정 완료
    REJECTED,               // 판매자가 주문 요청 거절
    CANCELLED               // 주문 취소 완료
}
