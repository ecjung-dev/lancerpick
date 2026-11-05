# 주문 알림·아웃박스

작성일: 2026-11-05. 담당: 정은찬. 브랜치: `feature/order-notifications`. 기준: [4인 고도화 설계](advanced-design-four-person.md) §6, [DB 설계](advanced-db-design.md) §7, V5 `outbox_events`·`notifications`.

## 범위

기존 MVP(LEGACY_V1) 주문의 상태 변경을 상대 당사자에게 알린다. V2 계약 주문·채팅·이메일·실시간 전송(WebSocket)·Redis는 이번 범위가 아니다. 새 테이블·마이그레이션은 추가하지 않았다(V5 스키마 사용).

## 처리 흐름

1. 주문 명령 서비스가 주문 이력을 저장한 직후 `OrderEventPublisher.publish`를 호출한다. 이벤트는 **같은 트랜잭션**(`MANDATORY`)에 `PENDING`으로 저장된다. 이벤트 저장이 실패하면 주문 변경도 롤백된다.
2. `OutboxDispatchScheduler`가 2초 간격(`lancerpick.notifications.dispatch-delay-ms`)으로 기한이 된 이벤트를 최대 100건 고른다.
3. `OutboxDispatcher`는 이벤트마다 **별도 트랜잭션**에서 행을 잠그고 상태를 다시 확인한 뒤 알림을 만들고 `COMPLETED`로 바꾼다. 여러 처리기가 같은 이벤트를 골라도 한 번만 완료된다.
4. 처리 실패는 다른 이벤트를 막지 않는다. 시도 수를 늘리고 30초부터 두 배씩(최대 1시간) 미룬다. 5회 실패하면 `FAILED`로 멈춘다. 알림 실패는 이미 커밋된 거래를 되돌리지 않는다.

`lease_owner`·`lease_until` 컬럼은 트랜잭션 밖 장시간 처리용으로 남겨 두었고, 현재 처리는 DB 안에서 끝나므로 사용하지 않는다. `FAILED` 이벤트의 재처리 명령은 관리자 고도화(김창재)에서 연결할 후속 범위다.

## 수신자와 유형

- 행동한 당사자를 제외한 상대 당사자에게 알린다. 시스템 처리(actor 없음)는 양측에 알린다(V2 타이머 대비, 현재 LEGACY 경로에는 없음).
- 유형은 `ORDER_` + 주문 이력 행동이다. 취소 요청 승인은 같은 트랜잭션의 `ORDER_CANCELLED`와 수신자가 같아 별도 알림을 만들지 않는다.
- 알림 UNIQUE `(recipient_id, event_id, type)`로 같은 이벤트의 중복 알림을 막는다.
- 처리기는 페이로드의 수신자가 현재 주문의 구매자·판매자인지 다시 확인하고, 아니면 이벤트를 실패 처리한다.
- 읽음 처리는 `read_at IS NULL` 조건부 UPDATE라 동시 요청에도 먼저 기록한 시각을 덮어쓰지 않는다.
- 페이로드에는 주문 ID·이력 ID·행동·수신자 ID만 넣는다. 사유·요구사항·결제 정보는 넣지 않는다. 처리 실패 로그에는 이벤트 ID와 예외 유형만 남긴다.

## API·화면

API는 [API 명세 7-1](api-spec.md)을 따른다. 화면은 다음과 같다.

- 헤더 회원 메뉴의 "알림" 링크와 읽지 않은 개수(99 초과 시 99+). 30초마다 다시 조회한다. 실시간 전송은 아니다.
- `/notifications`: 최신순 목록, 읽지 않음 구분, 페이지는 URL `?page=`. 항목을 누르면 읽음 처리 후 주문 상세로 이동한다. "모두 읽음"은 처리 중 비활성화하며 자동 재시도하지 않는다. 결과와 관계없이 목록·개수를 다시 조회한다.
- 로그인 필요(401)·서버 오류·빈 목록을 구분해 표시한다. 계정 전환 시 `accountCache.ts`가 auth 외 조회를 모두 지우므로 이전 계정의 알림이 남지 않는다.

## 다른 영역 영향

- 주문 서비스 8개(생성·수락/거절·결제·납품·수정·확정·취소·취소 요청)에 이벤트 발행 호출을 추가했다. 기존 응답·오류 계약은 바꾸지 않았다.
- `features/auth/components/MemberNav.tsx`(김창재)에 알림 링크 한 줄, `app/router.tsx`에 `/notifications` 경로를 추가했다.
- `@EnableScheduling`을 처음 도입했다. 테스트 프로필과 DB 없는 테스트는 `lancerpick.notifications.dispatch-enabled=false`로 스케줄러를 끈다.

## 검증

2026-11-05 로컬 실행(커밋 전 작업 트리):

- 백엔드 `clean build`: 로컬 테스트 DB(MySQL, `lancerpick_test`)에서 V1~V6·JPA validate 포함 **664건 통과, 실패·오류·건너뜀 0**. 신규 `NotificationIntegrationTests` 10건 포함(Codex 리뷰 반영 후 재실행).
  - 생성 이벤트 저장·상대방 1회 알림, 수락 알림과 목록 응답(스냅샷 상품명·UTC 시각), 본인 알림만 읽음·멱등, 모두 읽음의 본인 한정, 먼저 기록한 읽음 시각 유지, 주문 당사자가 아닌 수신자 페이로드 실패 처리, 이벤트 저장 실패 시 주문 롤백, 전달 실패 재시도·백오프·5회 후 FAILED, 동시 처리 1회 완료, 인증·CSRF·페이지 크기 검증.
- 프런트: `typecheck`·`lint`·`test:api` 135건(신규 6건)·`build` 통과.

실행하지 않은 검증: 동시 읽음 요청 경합의 직접 재현(조건부 UPDATE `read_at IS NULL`로 보장, 순차 검사만 실행), 실제 서버 기동 후 스케줄러 자동 처리와 브라우저 화면 확인, 320px 배치, 원격 CI. 스케줄러는 테스트에서 끄고 처리기를 직접 호출해 검증했다.
