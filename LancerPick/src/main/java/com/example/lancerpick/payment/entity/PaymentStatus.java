package com.example.lancerpick.payment.entity;

/** 기존 payments.status 저장 문자열과 일치한다. 알 수 없는 값은 정상 상태로 보정하지 않는다. */
public enum PaymentStatus {
  PAID,
  REFUNDED
}
