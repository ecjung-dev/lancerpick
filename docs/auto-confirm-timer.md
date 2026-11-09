# 검수 무응답 자동 구매 확정 (E7)

## 구매자 보안 잠금 연결(U8) — 2026-11-09, `feature/security-lock-timer-stop`(정은찬)

[개편 정책](ui-renewal-member-policy.md) §11.3·§16 U8 결정과 [제재 기반](member-sanction-foundation.md)(#134)의 `SecurityLockTimerStopper` 계약을 연결했다. 일반 이용 제한은 기존 타이머를 그대로 둔다. 판매자 보안 잠금도 영향이 없다. DB·Flyway 변경 없음.

- **중단**: `order/service/SecurityLockTimerStops`가 제재 커밋 후 호출된다. 그 구매자의 주문 중 **잠금 적용 시각까지 시작된** `ACTIVE` 타이머만 대상으로, 타이머마다 별도 READ_COMMITTED 트랜잭션(`ReviewTimerService.stopForSecurityLock`)에서 구매자 회원 공유 잠금 → 주문 배타 잠금 후 다시 확인해 `CANCELLED`/`ADMIN_STOP`으로 끝낸다. `ended_by`는 잠금을 적용한 관리자, 사유는 고정 문구 "보안 잠금: 구매자 계정 보호 조치로 자동 구매 확정 타이머를 중단했습니다."다(이력이 판매자에게도 보이므로 제재 사유 원문은 싣지 않는다). 이력 `TIMER_ADMIN_STOPPED`·양측 알림은 관리자 해제와 같다. 만료 시각이 지났지만 아직 처리 전인 타이머도 중단한다(관리자 해제와 다른 점). 이미 끝난 타이머·잠금 이후 시작된 타이머는 건너뛰어 중복 전달에 멱등이다. 하나라도 실패하면 예외로 알려 제재의 전달 기록이 미처리로 남는다.
- **시작 차단**: 판매자 시작은 실행 회원 → 구매자 회원 공유 잠금 → 주문 잠금 순서로 얻고, 구매자가 현재 보안 잠금이면 409 `BUYER_SECURITY_LOCKED`. 잠금 적용(구매자 회원 배타 잠금)과 직렬화돼 "잠금 직전 시작된 타이머는 중단, 잠금 뒤 시작은 거절" 둘 중 하나가 된다. 조회의 `canStart`도 false이고 화면은 "시작 보류"로 안내한다.
- **만료 재검사**: 만료 처리도 구매자 회원 공유 잠금 → 주문 잠금 후, 타이머 시작 이후(같은 시각 포함)에 적용된 구매자의 가장 최근 보안 잠금이 있으면 **현재 해제·만료됐더라도** 자동 확정 대신 위와 같이 중단한다. 커밋 후 전달이 실패하거나 서버가 멈춘 사이 잠금이 끝나도 옛 타이머가 자동 확정되지 않는다. 만료 24시간 전 사전 안내도 같은 조건이면 보내지 않는다.
- **해제 후**: 같은 기산점 재시작 금지(`TIMER_ADMIN_STOPPED`)가 그대로 적용돼 자동 재개되지 않는다. 구매자 응답 또는 기존 문의·관리자 처리로 건별 해결한다. 잠금 중 타이머가 없었던 주문은 해제 후 정상 시작할 수 있다. 이미 끝난 자동 확정·지급은 되돌리지 않는다.
- **남은 것**: 미처리 전달 재시도(`SecurityLockDelivery.retryPending`)의 스케줄러 연결은 하지 않았다. 실패분도 만료 재검사로 자동 확정은 막히지만 타이머는 만료 시각까지 `ACTIVE`로 보인다. 관리자 제재 API·화면(황준범)이 없어 실제 브라우저 확인은 아직이다.

2026-11-06, `feature/v3-auto-confirm-timer`(정은찬). main `0f67606` 기준 작업 브랜치에서 구현해 #99(`eb6b40e`)로 main에 반영됐다. 업무 정책은 [결제 금액 보관 정책](full-funding-policy.md) §2, 금액 처리는 [금액 보관 구현](full-funding-payment.md)을 따른다. CONTRACT_V3 주문에만 적용하며 DB 변경(Flyway)은 없다. V6·V7의 `order_response_timers`·`order_closures`·`order_histories`를 그대로 사용한다.

## 흐름

1. 납품(또는 재납품)으로 `AWAITING_CONFIRMATION`이 되면 **검수 기산점**이 생긴다. 기산점 = 마지막 납품 시각과, 거절·철회된 취소 요청의 처리 시각 중 늦은 값.
2. 기산점 + 48시간 이후 **판매자가** 7일 타이머를 시작한다. 시스템이 자동으로 시작하지 않는다. 타이머는 `AUTO_CONFIRM`, 대상 납품(`delivery_id`)과 같은 납품의 회차(`cycle_no`)를 기록한다.
3. 시작 + 168시간이 지나면 만료 스케줄러가 시스템 구매 확정을 한다. 수동 확정과 같은 10% 수수료·판매자 구매용 적립금 지급·정산·보관금 해제를 같은 트랜잭션에서 처리하고, 종료 기록은 `reason=AUTO_CONFIRMED`·`actor_type=SYSTEM`·`source_timer_id`, 주문 이력은 `COMPLETED`(SYSTEM, `actor_id` NULL, 원천 키 `AUTO_CONFIRM:<timerId>`), 타이머는 `EXPIRED`/`TIMEOUT`이다.

| 구매자 응답(만료 전) | 타이머 | 다음 기산점 |
| --- | --- | --- |
| 수정 요청 | `CANCELLED`/`BUYER_RESPONSE` | 재납품 시각 |
| 취소 요청 | `CANCELLED`/`BUYER_RESPONSE`. 대기 중에는 시작 불가 | 거절·철회 시각(같은 납품의 다음 회차). 승인되면 주문 취소 |
| 구매 확정 | `CANCELLED`/`ORDER_CLOSED` 후 정상 확정 | 없음 |

- **만료 우선**: 활성 타이머의 만료 시각 이후에 들어온 구매자 응답·관리자 해제는 타이머를 취소하지 않고 409 `AUTO_CONFIRM_DUE`로 응답한다. 스케줄러가 곧 자동 확정한다. 판단은 모두 주문 배타 잠금 아래 서버 시각으로 한다.
- **관리자 해제**: `ReviewTimerService.adminStop(adminId, orderId, reason)`. 2026-11-09 김창재가 K5 API·화면을 연결했다(`feature/admin-cancellation-inquiries`): `GET /api/admin/review-timers`(진행 중 타이머, 만료 이른 순)·`POST /api/admin/orders/{orderId}/review-timer/stop`(reason, [API 9-2절](api-spec.md#9-2-관리자-자동-구매-확정-타이머-해제-김창재)), 화면 `/admin/review-timers`. ADMIN 역할·사유 1~1000자 확인, `CANCELLED`/`ADMIN_STOP`, 이력 `TIMER_ADMIN_STOPPED`. 해제된 기산점으로는 다시 시작할 수 없고 새 납품이나 취소 요청 처리 이후 다시 시작할 수 있다(2026-11-06 조장 결정, [정책](full-funding-policy.md) §2-5에 반영).
- 납기 초과·미납품은 자동 확정 사유가 아니다(검수 대기에서만 시작).

## API

| API | 권한·처리 |
| --- | --- |
| `GET /api/orders/{id}/review-timer` | 구매자·판매자. `Cache-Control: no-store`. 타인·없는 주문·V3가 아닌 주문은 404 |
| `POST /api/orders/{id}/review-timer` | 판매자, 본문 없음·CSRF 필요. 이미 활성 타이머가 있으면 새로 만들지 않고 현재 상태를 돌려준다 |

응답 필드: `status`, `basisAt`, `eligibleAt`, `startedAt`, `expiresAt`(UTC ISO 8601, 해당 없으면 null), `canStart`(판매자이고 `STARTABLE`이며 구매자가 보안 잠금 중이 아닐 때만 true).

| status | 의미 |
| --- | --- |
| `NOT_IN_REVIEW` | 검수 대기가 아님(화면에서 숨김) |
| `CANCELLATION_PENDING` | 취소 요청 대기 |
| `WAITING` | 기산점 + 48시간 전 |
| `STARTABLE` | 판매자가 시작 가능 |
| `ADMIN_STOPPED` | 현재 기산점의 타이머를 관리자가 해제 |
| `ACTIVE` | 진행 중 |
| `AUTO_CONFIRMED` | 타이머 만료로 구매 확정됨 |

| HTTP·코드 | 조건 |
| --- | --- |
| 403 `FORBIDDEN` | 판매자가 아닌 시작 요청, 관리자가 아닌 해제 |
| 409 `TIMER_NOT_ELIGIBLE` | 기산점 + 48시간 전 |
| 409 `CANCELLATION_PENDING` | 취소 요청 대기 중 시작 |
| 409 `ORDER_STATE_CONFLICT` | 검수 대기가 아닌 주문 |
| 409 `TIMER_ADMIN_STOPPED` | 관리자가 해제한(보안 잠금 중단 포함) 같은 기산점으로 재시작 |
| 409 `BUYER_SECURITY_LOCKED` | 구매자 보안 잠금 중 시작 |
| 409 `TIMER_NOT_ACTIVE` | 활성 타이머 없이 관리자 해제 |
| 409 `AUTO_CONFIRM_DUE` | 만료 시각이 지난 타이머가 있는 주문의 구매 확정·수정 요청·취소 요청·관리자 해제 |
| 409 `ORDER_POLICY_NOT_READY` | V3가 아닌 주문의 시작 요청 |

기존 금액 처리의 409 `TIMER_INTEGRATION_NOT_READY`는 없앴다. 결제·승인 환불 시점에 활성 타이머가 있으면 앞 단계에서 끝났어야 하므로 500 `DATA_INTEGRITY_ERROR`다.

## 스케줄러·트랜잭션

- `ReviewTimerExpiryScheduler`가 `lancerpick.order.review-timer.expiry-delay-ms`(기본 30초) 간격으로 실행 시작 시각까지 만료된 타이머를 50건씩 (만료 시각, ID) 커서로 끝까지 순회한다. 계속 실패하는 타이머가 앞쪽에 쌓여도 같은 실행에서 뒤의 정상 타이머를 처리한다(Codex 리뷰 반영). `lancerpick.order.review-timer.expiry-enabled=false`로 끌 수 있으며 test 프로필은 끄고 `ReviewTimerService.expireDue`를 직접 호출한다.
- 타이머마다 별도 READ_COMMITTED 트랜잭션: 구매자 회원 공유 잠금(U8, 보안 잠금 재검사용) → 주문 배타 잠금 → 타이머 재확인(ACTIVE·만료) → 구매자 보안 잠금 재검사 → 판매자 지갑 배타 잠금. 한 건의 실패(예: 아웃박스 저장 실패)는 그 주문 전체를 롤백하고 타이머를 ACTIVE로 남겨 다음 실행에서 다시 시도한다. 실패는 타이머 ID와 오류 코드만 로그에 남긴다.
- 여러 서버가 동시에 실행해도 주문 잠금 후 재확인하므로 한 번만 지급한다.

## 알림·화면

- 이력·아웃박스: `TIMER_STARTED`(구매자에게 `ORDER_TIMER_STARTED`), `TIMER_ADMIN_STOPPED`(양측), 자동 확정 `COMPLETED`(양측에 `ORDER_AUTO_CONFIRMED`). 구매자 응답에 따른 `TIMER_CANCELLED`는 이력만 남기고 알림을 보내지 않는다(함께 기록되는 수정 요청·취소 요청·구매 확정 알림이 대신함).
- 만료 24시간 전 구매자 사전 안내(`ORDER_AUTO_CONFIRM_SOON`)와 V3 금액 알림 문구는 E9에서 추가했다([V3 알림](v3-notices.md)).
- 주문 상세(V3)에 `ReviewTimerPanel`: 상태 안내, 자동 확정 예정 시각, 서버 `canStart`일 때만 판매자 시작 버튼. 시작 요청은 자동 재전송하지 않고 성공·실패 모두 `['wallet']` 조회를 다시 한다. 조회 키 `['wallet', 'order-review-timer', id]`는 기존 계정 전환 정리 대상이다. 검수 대기 중 상태(`WAITING`·`STARTABLE`·`ACTIVE`·`CANCELLATION_PENDING`·`ADMIN_STOPPED`)는 시간 경과나 상대방 행동(판매자의 타이머 시작, 취소 요청 처리, 재납품)으로 바뀔 수 있어 1분마다 다시 조회하고, `NOT_IN_REVIEW`·`AUTO_CONFIRMED`와 백그라운드 탭·조회 실패 시에는 멈춘다, 상태 변화(자동 확정 완료 등)를 발견하면 `['wallet']`을 무효화해 주문 상세·대금도 다시 확인한다(Codex 리뷰 반영).
- 거래 이력에서 실행 회원이 없는 `COMPLETED`는 "자동 구매 확정 · 시스템"으로 표시한다.

## 검증

- 백엔드 `ReviewTimerIntegrationTests` 10건(로컬 MySQL 8.4 `lancerpick_test`, 실제 커밋·잠금): 48시간 전 시작 거부·판매자 전용, 재전송 시 타이머 1개, 만료 후 응답 거부(만료 우선)와 1회 시스템 확정·금액·종료 기록·SYSTEM 이력·알림 유형, 자동 확정 후 확정/결제 재전송 무지급, 수정 요청 취소→재납품 새 기산점, 취소 요청 취소→거절 시각 새 기산점·회차 2, 수동 확정 시 타이머 종료, 관리자 해제 권한·사유·재시작 차단, 만료된 타이머 해제 거부, 동시 만료 실행 1회 지급, 아웃박스 실패 시 전체 롤백 후 재시도, 배치 크기 1에서도 계속 실패하는 앞 타이머 뒤의 정상 타이머 처리, HTTP 권한·CSRF·no-store. 48시간·7일 경과는 DB 시각을 같은 폭으로 과거로 옮겨 재현했다.
- 프런트 `tests/review-timer.test.mjs` 5건: 요청 경로·no-store·본문 없는 POST, 상태별 문구, `canStart`만으로 버튼 표시, SYSTEM 이력·새 알림 문구, 상태별 재조회 간격. 프런트 typecheck·lint·test:api 177건·build 통과.
- 백엔드 전체 `clean build`(로컬 `lancerpick_test`) 796건 통과, 실패·오류·건너뜀 0. DB 없이 뜨는 `DefaultCatalogApiTests` 컨텍스트에는 `ReviewTimerService`·`ReviewTimers` mock과 만료 스케줄러 끄기 설정을 추가했다.
- 실제 서버 스케줄러 동작과 브라우저 화면 확인은 하지 않았다.

실서버 확인(2026-11-06, main `9f9aa8f`): 개인 개발 DB에서 테스트 주문 한 건의 납품·타이머 시각을 과거로 옮겨 실제 만료 스케줄러의 자동 구매 확정(SYSTEM 완료·`EXPIRED`/`TIMEOUT`·closure `AUTO_CONFIRMED`·양측 알림)과 브라우저 알림·후기 폼을 확인했다. 시각을 직접 옮긴 확인이며 실제 시간 경과 검증은 아니다. 상세는 [V3 알림](v3-notices.md#실서버브라우저-확인--2026-11-06-main-9f9aa8f).

화면 확인(2026-11-06, main `7329be1`): 주문 23의 납품 시각만 49시간 앞당겨 판매자 패널 "시작 가능" → 시작 버튼 → "진행 중"(만료 예정 시각 표시)과 구매자 패널의 같은 안내(버튼 없음), 320px 배치를 확인했다. 상세는 [활성화 게이트 8절](v3-activation-gate.md#8-남은-화면-확인--2026-11-06).
