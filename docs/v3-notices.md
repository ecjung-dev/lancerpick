# V3 기한 통지(E8)·보관/지급/반환·자동 확정 사전 안내(E9)

2026-11-06, `feature/v3-deadline-autoconfirm-notices`(정은찬), main 반영(#105). 작업 기준은 main `2188f8d`. CONTRACT_V3 주문에만 적용하며 LEGACY_V1 알림은 바꾸지 않는다. DB 변경(Flyway) 없음: V6의 `DEADLINE_NOTIFIED` 행동·`source_event_key` UNIQUE·`details` JSON과 V5 `outbox_events.event_id` UNIQUE를 그대로 쓴다. 정책은 [결제 금액 보관 정책](full-funding-policy.md) §1·§2-6, 전달 구조는 [주문 알림·아웃박스](notifications.md)를 따른다.

## E8 납기·재납품 기한 통지

| 사건 | 이력(같은 트랜잭션) | 원천 키 | 알림 유형(양측) |
| --- | --- | --- | --- |
| V3 전액 결제 | `DEADLINE_NOTIFIED`, IN_PROGRESS 유지 | `DEADLINE:PAYMENT:<결제 ID>` | `ORDER_DELIVERY_DUE_NOTIFIED` |
| V3 수정 요청 | `DEADLINE_NOTIFIED`, REVISION_IN_PROGRESS 유지 | `DEADLINE:REVISION:<수정 요청 ID>` | `ORDER_REDELIVERY_DUE_NOTIFIED` |

- 실행자는 SYSTEM(`actor_id` NULL)이라 구매자·판매자 모두에게 알린다. 이력 시각은 기준 시각(결제·수정 요청 시각)과 같다.
- `details`: `kind`(DELIVERY_DUE/REDELIVERY_DUE), `basisAt`, `dueAt`(UTC ISO 8601), `deliverySequence`(다음 납품 회차 1/2), `contractVersion`(서명된 현재 계약 버전). 기한 값은 `orders.due_at`·`revision_requests.due_at`과 같다.
- **중복 없음**: 원천 키가 결제·수정 요청 ID로 정해지고 `uk_order_history_source` UNIQUE라 같은 사건으로 두 번 기록될 수 없다(재전송은 기존 기록 확인 경로라 통지를 만들지 않는다). 통지 저장이 실패하면 결제·수정 요청도 롤백된다.
- 최초 계약 PDF는 갱신하지 않는다. 거래 이력 화면은 "납기 통지 · 시스템"/"재납품 기한 통지 · 시스템"으로 표시한다.
- 구현: `order/service/DeadlineNotices`(호출: `FullFundingService.pay`, `OrderRevisionService.request`), `OrderHistory.deadlineNotified`.

## E9 V3 금액 알림 문구

알림 유형은 이벤트 저장 시점에 정해져 이후 정책 변경에 바뀌지 않는다. V3 주문만 아래 유형으로 바꾸고 나머지는 기존 `ORDER_` + 행동을 유지한다.

| 이력 | V3 알림 유형 | 수신자 |
| --- | --- | --- |
| PAID | `ORDER_FUNDS_HELD` (대금 보관) | 판매자 |
| COMPLETED(구매자) | `ORDER_FUNDS_RELEASED` (수수료 제외 적립금 지급) | 판매자 |
| COMPLETED(SYSTEM) | `ORDER_AUTO_CONFIRMED` (자동 확정·적립금 지급, 문구 보강) | 양측 |
| CANCELLED(결제 후 상태에서) | `ORDER_FUNDS_REFUNDED` (보관금 전액 반환) | 행동하지 않은 당사자(관리자 승인 취소는 양측) |
| CANCELLATION_REJECTED(관리자 실행) | `ORDER_CANCELLATION_ADMIN_REJECTED` (운영 문의 검토 거절, 2026-11-06 K4) | 양측 |

결제 전 V3 취소는 기존 `ORDER_CANCELLED`다. 잔금 미지급 종료 알림은 만들지 않는다.

## E9 자동 구매 확정 사전 안내

- 만료 24시간 전부터 만료 전까지(`OrderResponseTimer.NOTICE_BEFORE_HOURS`) 활성 타이머의 **구매자**에게 `ORDER_AUTO_CONFIRM_SOON`을 한 번 보낸다. 판매자는 타이머를 시작한 당사자라 보내지 않는다.
- 기존 만료 스케줄러(`ReviewTimerExpiryScheduler`, 기본 30초)가 만료 처리 뒤 `ReviewTimerService.noticeDue(50)`을 호출한다. 후보는 (만료 시각, ID) 커서로 끝까지 순회하고, 타이머마다 별도 트랜잭션에서 주문 배타 잠금 → 타이머가 활성·구간 안·검수 대기인지 재확인 → 이벤트 저장 순서다.
- 상태 변화가 아니므로 주문 이력은 남기지 않는다. 이벤트 ID를 타이머 ID로 정한 UUID(`AUTO_CONFIRM_NOTICE:<timerId>` 이름 기반)로 저장해 `event_id` UNIQUE가 여러 서버·반복 실행의 중복을 막는다. 이미 안내한 타이머는 잠금 없이 먼저 건너뛴다.
- 안내 후 구매자가 수정·취소 요청·확정을 하면 타이머는 기존대로 끝나고 안내는 그대로 남는다. 만료 시각이 이미 지난 타이머에는 안내 대신 자동 확정 결과(`ORDER_AUTO_CONFIRMED`)만 간다.
- 결과 알림(`ORDER_AUTO_CONFIRMED`)·타이머 시작(`ORDER_TIMER_STARTED`)·관리자 해제(`ORDER_TIMER_ADMIN_STOPPED`)는 E7 그대로다.

## 화면

`features/notifications/messages.ts`에 새 유형 6개 문구를 추가하고 `ORDER_AUTO_CONFIRMED` 문구에 적립금 지급을 덧붙였다. `features/orders/history.ts`가 `DEADLINE_NOTIFIED`를 대상 상태로 구분해 표시한다. 새 API·queryKey는 없다.

## 검증

2026-11-06 로컬 실행(커밋 전 작업 트리, 로컬 MySQL 8.4 `lancerpick_test`):

- 백엔드 전체 `clean build` **828건 통과, 실패·오류·건너뜀 0**.
- `ReviewTimerIntegrationTests` 신규 5건(실제 커밋·잠금): 결제 기한 통지의 SYSTEM 이력·details(기준·납기·회차·계약 버전)와 `orders.due_at` 일치, 결제 재전송 시 통지 1건 유지, 같은 원천 키 재기록 시 UNIQUE 위반, 양측 알림과 V3 `ORDER_FUNDS_HELD`; 수정 요청의 재납품 기한 통지와 `revision_requests.due_at` 일치; 수동 확정 `ORDER_FUNDS_RELEASED`·승인 환불 `ORDER_FUNDS_REFUNDED`; 만료 24시간 전 사전 안내의 구매자 1회 전달(동시 실행 2개 포함)·고정 이벤트 ID·상태 불변; 수정 요청으로 끝난 타이머와 이미 만료된 타이머에는 안내하지 않음.
- 기존 테스트 보정: `ContractV3FulfillmentApiIntegrationTests` fixture에 서명 계약 행 추가·이력 기대값에 `DEADLINE_NOTIFIED` 추가, DB 없는 `DefaultCatalogApiTests`에 `DeadlineNotices` mock 추가.
- 프런트 `typecheck`·`lint`·`test:api` 199건(신규 2건)·`build` 통과(로컬 `node_modules`가 #103 이전 상태라 `npm ci` 후 실행).
- 당시 실행하지 않은 검증: 실제 서버 스케줄러의 사전 안내 자동 실행, 브라우저 알림·이력 화면 확인, 원격 CI.

### 실서버·브라우저 확인 — 2026-11-06 (main `9f9aa8f`)

로컬 실서버(`bootRun`, 개인 개발 DB, 실행 인자로만 `lancerpick.order.full-funding-creation-enabled=true`, 설정 파일은 바꾸지 않음) + Vite 개발 서버에서 새로 가입한 두 계정으로 V3 주문 2건을 HTTP API로 진행했다. 주문·수락·계약 초안·실제 PDF 생성·양측 서명·결제·납품·수정 요청·재납품·확정/취소 요청·승인은 화면 버튼 대신 같은 API로 실행했다.

- 주문 A(결제 → 납품 → 수정 요청 → 재납품 → 구매 확정): `DEADLINE_NOTIFIED` 2건(SYSTEM, `DEADLINE:PAYMENT:<결제 ID>`/`DEADLINE:REVISION:<요청 ID>`, `deliverySequence` 1→2, `dueAt`=기준+3일이 `orders.due_at`·`revision_requests.due_at`과 일치). 대금 `RELEASED`, 수수료 5,000원·판매자 지급 45,000원.
- 주문 B(결제 → 구매자 취소 요청 → 판매자 승인): 대금 `REFUNDED`, 반환 50,000원.
- 아웃박스가 모두 `COMPLETED`로 처리됐고 알림 수신자가 표와 같다: `ORDER_FUNDS_HELD`·`ORDER_FUNDS_RELEASED`는 판매자, 기한 통지 2종은 양측, `ORDER_FUNDS_REFUNDED`는 구매자.
- 브라우저(두 계정 실제 로그인): 알림 목록에 새 문구 4종(납기·재납품 기한·보관·지급·반환)이 표시되고, 주문 상세 거래 이력에 "납기 통지 · 시스템 / 상태 유지: 진행 중", "재납품 기한 통지 · 시스템 / 상태 유지: 수정 진행 중"이 표시된다. 대금 패널은 지급 완료·수수료·지급액을 표시한다.
- [G1 금액 불변식 조회](v3-activation-gate.md#4-금액-불변식-조회)가 두 주문에 대해 위반 0행이다.
- 발견(이 기능 범위 밖, 계약 영역): 결제·완료 뒤에도 계약 패널이 "주문 상세에서 전액 적립금 결제를 진행해 주세요."를 표시한다(`ContractPanel.tsx`가 계약 `SIGNED`만 보고 주문 상태를 보지 않음). → `test/v3-activation-gate`에서 보완: 결제 안내는 주문이 `AWAITING_PAYMENT`일 때만 표시(V2 안내 포함), 회귀 테스트 `contract-pdf.test.mjs` 추가.
- 사전 안내·자동 확정 스케줄러(주문 C): 기준 시간(48시간·7일·24시간)이 코드 상수라, 사용자 승인을 받아 **개인 DB의 이 테스트 주문 한 건만** 시각을 과거로 옮겼다. 납품 시각 -49시간 → 판매자가 HTTP로 타이머 시작 → 타이머(기준·시작 가능·시작·만료 시각 함께, CHECK 유지)와 납품 시각을 -145시간 이동해 만료 23시간 전으로 맞춤 → 실제 만료 스케줄러(30초)가 구매자에게만 `ORDER_AUTO_CONFIRM_SOON` 1건을 보냄(이름 기반 UUID 이벤트 ID, 추가 주기 뒤에도 1건 유지, 주문 상태 불변) → 다시 -24시간 이동해 만료 → 스케줄러가 SYSTEM `COMPLETED` 이력·타이머 `EXPIRED`/`TIMEOUT`·closure `AUTO_CONFIRMED`(수수료 5,000원·지급 45,000원·`source_timer_id`)·양측 `ORDER_AUTO_CONFIRMED`를 남김. 브라우저에서 구매자 알림 3종(타이머 시작·24시간 전 안내·자동 확정 결과) 문구와 자동 확정 후 후기 작성 폼을 확인했다. 세 주문 모두 G1 불변식 위반 0행.
- 시각을 직접 옮긴 확인이므로 "실제 시간 경과"를 검증한 것은 아니다. 타이머 시작 이력·알림 시각은 옮기지 않아 타이머 시각보다 늦게 남아 있다.
