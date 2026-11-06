package com.example.lancerpick.notification.entity;

/**
 * `outbox_events.last_error_code`에 저장하는 고정 오류 코드. 예외 클래스 이름·메시지·페이로드는 저장하지 않는다.
 * 재처리 화면(K6)과 운영 조회는 이 코드만 기준으로 삼는다. 코드를 바꾸거나 지우지 말고 새 코드를 추가한다.
 */
public enum OutboxErrorCode {
  /** 지원하지 않는 집계 유형 또는 스키마 버전. 다시 시도해도 성공할 수 없다. */
  UNSUPPORTED_EVENT(true),
  /** JSON 형식·필수 필드·수신자 ID 형식 오류. */
  INVALID_PAYLOAD(true),
  /** 페이로드 수신자가 현재 집계(주문)의 당사자가 아님. */
  INVALID_RECIPIENT(true),
  /** 이벤트가 가리키는 집계가 없음. */
  AGGREGATE_NOT_FOUND(true),
  /** 행 잠금 대기 초과·교착 등 일시적 잠금 실패. 백오프 후 다시 시도한다. */
  LOCK_UNAVAILABLE(false),
  /** 그 밖의 처리 실패. 백오프 후 다시 시도한다. */
  PROCESSING_FAILED(false);

  private final boolean permanent;

  OutboxErrorCode(boolean permanent) {
    this.permanent = permanent;
  }

  /** true면 첫 실패에서 바로 FAILED로 멈춘다. */
  public boolean permanent() {
    return permanent;
  }
}
