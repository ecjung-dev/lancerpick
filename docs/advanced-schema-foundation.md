# 고도화 전체 업무 스키마 기반 — V5/V6

2026-11-05 신규 정책: CONTRACT_V3는 [전액 보관·판매자 적립금 지급·자동 구매 확정](full-funding-policy.md)을 따른다. 로컬 V7은 업무 테이블 30개와 새 모드를 준비한다. 아래 선금/잔금·보상·V1~V6/29개 설명은 해당 버전의 이력이다. 신규 V3 금액 명령·스케줄러의 완료를 뜻하지 않는다.

작성일: 2026-11-05. 브랜치: feature/advanced-schema-foundation. 선행 기준: main c24f37d(PR #76). DB 변경 담당 황준범.

현재 반영 상태(2026-11-05): main `1e10d78`에 V5/V6와 기존 엔티티 호환 변경이 반영됐다. 아래 브랜치·검증 설명은 구현 당시 기록이다. 이번 문서 갱신은 DB 적용이나 재검증을 포함하지 않는다. 후속: `outbox_events`·`notifications`는 main `1941ad1`의 기존 주문 알림(#79, #80)에서 사용한다([주문 알림](notifications.md)). 나머지 테이블의 서비스 연결 상태는 [고도화 작업표](work-plan.md#고도화-작업표--2026-11-05)를 따른다.

## 실제 추가 범위

V1/V2의 17개 + V4 계약 2개 + V5 7개 + V6 3개 = **29개 업무 테이블**이다. 목표 신규 12개가 모두 존재한다. 선택 관심 상품·Spring Batch의 BATCH_* 시스템 테이블은 포함하지 않는다.

| 테이블 | 제공 기능의 데이터 | 기능 담당 |
| --- | --- | --- |
| order_contracts·contract_signatures | 현재 계약·문서·특약·양측 서명 | 정은찬·황준범 |
| order_chat_messages | 주문별 대화·순번·요청 중복 방지 | 황준범 |
| outbox_events·notifications | 이벤트 처리 상태·사용자 알림·읽음 | 정은찬(기존 주문 알림 반영 #79, #80) |
| account_challenges·auth_request_limits | 목적별 인증 증명·메일/인증 요청 제한 | 김창재 |
| support_inquiries·support_messages | 구매자 운영 문의·관리자 답변 | 김창재 |
| order_trade_terms | 신규 주문의 고정 비율·응답 기한 | 황준범·정은찬 |
| order_response_timers | 검수/잔금 기한·관리자 해제 근거 | 정은찬·황준범, 관리자 화면 김창재 |
| order_closures | 한 번의 종료 결과·선금 분배 | 황준범·정은찬 |

테이블 생성이 해당 기능 구현 완료를 의미하지 않는다. V5/V6에서는 새 REST API·프런트 화면·Redis·메일·Batch 작업을 켜지 않는다. 신규 10개 테이블의 기능별 엔티티/Repository는 각 담당자가 실제 기능과 함께 추가한다. 이미 존재하는 12개 중 계약 2개의 엔티티는 선행 PR에서 제공했다.

## 기존 테이블의 준비 컬럼

- members: email_verified_at·auth_version. 기존 회원 인증 시각은 NULL이며 인증 버전은 0. 실제 토큰/세션 버전 비교는 회원 기능에서 연결한다.
- orders: inspection_approved_at·terminated_at·chat_sequence와 새 고도화 상태. 기존 생성은 LEGACY_V1 유지.
- order_histories: nullable actor_id·actor_type·source_event_key·details JSON, 신규 행동/상태 CHECK. 기존 팩터리는 MEMBER와 회원 ID를 계속 저장한다.
- cancellation_requests: resolution_source와 주문 일치용 복합 키. 기존 승인/거절은 SELLER, 철회는 BUYER로 백필하고 엔티티도 같은 값을 저장하도록 보완했다. 관리자 처리 경로는 후속이다.
- settlements: settlement_type NORMAL 기본값. 보상 정산에는 NO_RESPONSE_COMPENSATION과 100bps를 요구한다. 기존 NORMAL 수수료 데이터는 재계산하지 않는다.
- wallet_topups: 복구 시도 수·다음 실행 시각·임대 토큰/만료. 실제 복구 실행기는 후속이다.
- deliveries: 타이머가 다른 주문의 납품을 참조하지 못하도록 복합 키 추가.

회원·주문·이력·취소·정산·충전의 추가 컬럼은 기존 엔티티에도 매핑했다. 주문/이력 enum은 새 값을 표현할 수 있지만 기존 주문 API가 이를 생성하도록 바꾸지는 않았다.

## 의도적으로 유지한 결제 제약

payments.uk_payments_order와 전액 환불 CHECK는 아직 유지한다. 단일 결제 조회를 사용하는 기존 API가 회차 두 건이나 부분 반환을 만나지 않도록 한 단계적 전환이다.

**테이블은 모두 준비됐지만 분할 결제를 실행할 준비가 끝난 것은 아니다.** 회차별 조회·계약 상태·원장·정산·HTTP 명령이 연결되면 후속 migration으로 주문당 단일 결제를 회차별 UNIQUE로, 전액 환불을 부분 반환 상태 CHECK로 전환한다. 전체 검토 SQL을 직접 실행해 먼저 풀지 않는다.

기존 도메인 서비스의 취소/검수/구매 확정 가드, 새 상태의 허용 행동·화면 표시·SYSTEM 이력 표시도 기능 활성화 전에 구현한다. 새 테이블이 있다고 raw SQL로 운영 주문을 새 상태로 전환하지 않는다.

## 적용 절차

최신 main의 코드·V5/V6를 함께 받아 기존 설정으로 서버를 실행한다. Flyway가 V1~V4 적용 이력을 보존하고 V5/V6만 추가 적용한다. 적용된 migration 파일을 수정하거나 전체 DB를 재생성하지 않는다.

docs/sql/advanced-db-candidate.sql은 최초 V1/V2 기준 전체 목표 검토본이다. V3~V6 적용 DB에 직접 실행하지 않는다. 다음 DDL도 황준범이 최신 migration 기준 차분으로 제공한다. 관심 상품·파일 납품·Batch 시스템 스키마는 선택 기능 착수 시 별도 추가한다.

## 검증

실행 명령: `python docs/scripts/test_advanced_schema.py --runtime`. 이 모드는 V1/V2에 기존 회원·지갑·주문·환불·해결된 취소 fixture를 넣고 V3~V6를 적용한다. MySQL 8.4.13에서 **33개 스키마 검사 통과**. 29개 테이블·기존 데이터 보존·정책/환불/취소/이력 백필·회원/채팅 기본값·현재 계약·타이머·종료 분배·토큰·요청 제한·아웃박스·알림·문의 제약을 검사했다.

검사는 자체 생성한 임시 컨테이너와 볼륨만 사용·정리한다. fixture는 DB 제약 검사용이며 전체 업무 시나리오나 HTTP 권한 검증을 의미하지 않는다. 개인 개발 DB에 직접 적용한 기록이 아니다.

전체 clean build에서 V1~V6·JPA validate를 포함해 651건을 실행했고 650건 통과, 기존 취소 fixture의 resolution_source 누락 1건이 실패했다. fixture에 SELLER를 추가한 뒤 해당 OrderCreationApiIntegrationTests 전체 24건을 재실행해 실패·오류·건너뜀 0으로 통과했다. 수정 후 운영 코드는 바꾸지 않았으며 전체 651건을 한 번에 다시 실행한 결과와 구분한다. 기존 철회 fixture도 BUYER를 명시하도록 보완했다.

위 구현 당시 검증에서는 개인 DB 적용·커밋·push를 수행하지 않았다. 이후 main 반영 상태는 문서 상단을 따른다. 테이블 수에 Batch 시스템 테이블을 포함했다고 표현하지 않는다.
