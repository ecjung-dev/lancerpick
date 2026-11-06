# 공통 이벤트 ID·버전·오류 계약 (C1)

작성일: 2026-11-06. 담당: 정은찬(아웃박스), 황준범(채팅 소켓, 검토). 브랜치 `feature/common-event-contract`. DB 변경(Flyway) 없음: V5 `outbox_events`의 `event_id` UNIQUE·`schema_version`·`last_error_code`를 그대로 쓴다. 관련: [주문 알림·아웃박스](notifications.md), [V3 알림](v3-notices.md), [채팅 실시간](order-chat-realtime.md), [API 공통 오류](api-spec.md).

## 1. 이벤트 ID

| 경우 | 규칙 | 사용처 |
| --- | --- | --- |
| 업무 행동마다 한 번 | 무작위 UUID(v4). `OutboxEvent.pending(type, schemaVersion, …)` | 주문 이력 기반 알림(`OrderEventPublisher.publish`) |
| 같은 원천에서 한 번만 | `OutboxEvent.nameBasedId(kind, sourceId)` = `"<kind>:<sourceId>"`의 이름 기반 UUID(v3) | 자동 구매 확정 사전 안내 `AUTO_CONFIRM_NOTICE:<timerId>` |

- 이름 기반 ID는 여러 서버·반복 실행에서 같은 값이 나오므로 `event_id` UNIQUE가 중복 저장을 막는다. 미리 `existsByEventId`로 건너뛰는 것은 잠금 비용을 줄이는 최적화일 뿐이고 최종 보장은 UNIQUE다.
- `kind`는 대문자·숫자·밑줄(`[A-Z][A-Z0-9_]{0,59}`)만 쓴다. 한 번 정한 kind는 바꾸지 않는다(바꾸면 이미 저장된 이벤트와 다른 ID가 되어 중복 방지가 깨진다). `AUTO_CONFIRM_NOTICE`는 #105에서 저장된 값과 같은 ID를 만든다(`OutboxEventContractTests`로 고정).
- 알림 중복은 `notifications (recipient_id, event_id, type)` UNIQUE가 한 번 더 막는다.

## 2. 스키마 버전

- 버전은 **집계 유형(aggregate_type)의 페이로드 형식** 기준이다. 현재 `ORDER`만 있고 버전은 `OrderEventPublisher.ORDER_SCHEMA_VERSION = 1`.
- 필드를 **추가만** 하면 버전을 올리지 않는다(처리기는 모르는 필드를 무시한다). 기존 필드의 의미를 바꾸거나 지우면 올린다.
- 올릴 때는 처리기가 이전 버전도 계속 읽도록 지원 목록에 함께 추가한다. 이미 저장된 `PENDING` 이벤트가 있기 때문이다.
- 처리기가 지원하지 않는 집계·버전은 다시 시도하지 않고 `UNSUPPORTED_EVENT`로 바로 `FAILED` 처리한다.
- 페이로드 공통 규칙: ID는 JSON 문자열, 시각은 UTC ISO 8601, 사유·요구사항·결제 정보·개인정보는 넣지 않는다. 수신자는 `recipientIds`(문자열 배열)이고 처리기가 현재 주문 당사자인지 다시 확인한다.

## 3. 아웃박스 오류 코드 (`last_error_code`)

예외 클래스 이름·메시지·페이로드를 저장하지 않고 아래 고정 코드만 저장한다(`notification/entity/OutboxErrorCode`). 재처리 화면(K6)과 운영 조회는 이 코드만 기준으로 삼는다. 코드는 바꾸거나 지우지 않고 추가만 한다.

| 코드 | 재시도 | 의미 |
| --- | --- | --- |
| `UNSUPPORTED_EVENT` | 안 함(즉시 FAILED) | 지원하지 않는 집계 유형·스키마 버전 |
| `INVALID_PAYLOAD` | 안 함 | JSON 형식·필수 필드·수신자 ID 형식 오류 |
| `INVALID_RECIPIENT` | 안 함 | 수신자가 현재 주문 당사자가 아님 |
| `AGGREGATE_NOT_FOUND` | 안 함 | 이벤트가 가리키는 주문이 없음 |
| `LOCK_UNAVAILABLE` | 백오프 후 최대 5회 | 잠금 대기 초과·교착(`PessimisticLockingFailureException` 계열) |
| `PROCESSING_FAILED` | 백오프 후 최대 5회 | 그 밖의 처리 실패 |

이번 변경 전 저장된 행에는 `UnsupportedEventException`·`IllegalStateException` 같은 클래스 이름이 남아 있을 수 있다. 데이터 이전은 하지 않으며, K6 화면은 모르는 값을 "기타 실패"로 표시하면 된다.

## 4. 채팅 소켓 오류 형식

REST 오류(`code`·`message`·`fieldErrors`·`traceId`)와 같은 필드를 쓰고, HTTP 상태가 없으므로 같은 의미의 `status`를 함께 싣는다.

```json
{
  "orderId": "11",
  "clientMessageId": "581f0401-3684-4992-a541-137179be9f70",
  "status": 409,
  "code": "CHAT_CLOSED",
  "message": "종료된 주문의 대화는 조회만 할 수 있습니다.",
  "fieldErrors": [],
  "traceId": "3f2c…"
}
```

- `/user/queue/chat-errors`의 업무 오류: `status`는 `BusinessException`의 HTTP 상태(404·409 등). 형식 오류는 400 `INVALID_REQUEST`(orderId·clientMessageId null), 예상치 못한 실패는 503 `CHAT_SEND_FAILED`.
- `traceId`는 전송 한 건마다 서버가 새로 만든 UUID이며 처리 중 로그 MDC에도 같은 값이 들어간다(HTTP의 `TraceIdFilter`와 같은 규칙, 클라이언트가 보낸 값은 쓰지 않는다). 예상치 못한 실패는 `traceId`와 예외 유형만 로그에 남긴다.
- 연결을 끊는 STOMP `ERROR` 프레임: 본문 `{"code","traceId"}`, `message` 헤더는 code. code는 `FORBIDDEN`·`CSRF_INVALID`·`AUTHENTICATION_REQUIRED`·`CHAT_RATE_LIMITED`만 쓴다.
- 프런트(`order-chat/realtime.ts` `chatErrorStatus`)는 서버의 `status`를 쓰고, 상태가 없는 이전 형식 프레임은 기존처럼 `CHAT_SEND_FAILED`만 503, 나머지는 409로 본다. `traceId`는 `ApiError.traceId`로 전달한다.

## 5. 범위 밖

- 실시간 알림 전송, Redis Pub/Sub(C4), 이벤트 스트림 외부 발행.
- 아웃박스 `lease_owner`·`lease_until` 사용(현재 처리는 DB 트랜잭션 안에서 끝남).
- `FAILED` 재처리 명령·화면(K6, 김창재 + 정은찬·황준범 서비스).
