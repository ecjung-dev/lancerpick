package com.example.lancerpick.order.entity;

/** cancellation_requests.status 저장 값. 이름이 DB 값·API 응답 값과 같으므로 바꾸지 않는다. */
public enum CancellationRequestStatus {
  PENDING,
  APPROVED,
  REJECTED,
  WITHDRAWN
}
