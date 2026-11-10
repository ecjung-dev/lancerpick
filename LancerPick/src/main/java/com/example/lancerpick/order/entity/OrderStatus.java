package com.example.lancerpick.order.entity;

public enum OrderStatus {
    REQUESTED,              // 주문 요청, 판매자 수락 대기
    AWAITING_PAYMENT,       // 판매자 수락 완료, 구매자 결제 대기
    IN_PROGRESS,            // 결제 완료, 작업 진행 중
    AWAITING_CONFIRMATION,  // 납품 완료, 구매자 확인 대기
    REVISION_IN_PROGRESS,   // 수정 요청에 따른 작업 진행 중
    COMPLETED,              // 구매 확정 완료
    REJECTED,               // 판매자가 주문 요청 거절
    CANCELLED,              // 주문 취소 완료
    AWAITING_CONTRACT,      // 고도화: 계약 서명 대기, 신규 API는 아직 비활성
    AWAITING_BALANCE,       // 고도화: 검수 승인 후 잔금 대기
    TERMINATED;            // 고도화: 무응답 종료·선금 분배

    /** 결제 뒤 작업이 이어지는 상태(작업 중·검수 대기·수정 중)인지 본다. 실제 결제 여부는 결제·보관금 기록으로 따로 확인한다. */
    public boolean isWorkOpen() {
        return this == IN_PROGRESS || this == AWAITING_CONFIRMATION || this == REVISION_IN_PROGRESS;
    }
}
