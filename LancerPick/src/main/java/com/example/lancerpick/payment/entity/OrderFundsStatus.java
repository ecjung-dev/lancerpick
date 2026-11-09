package com.example.lancerpick.payment.entity;

/** 결제 금액 보관·판매자 지급·구매자 반환 상태. DB의 기존 VARCHAR 값을 유지한다. */
public enum OrderFundsStatus {
  HELD,
  RELEASED,
  REFUNDED
}
