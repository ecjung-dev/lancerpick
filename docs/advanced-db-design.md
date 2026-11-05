# 랜서픽 고도화 DB 설계 v0.4


2026-11-05 신규 정책: CONTRACT_V3는 [전액 보관·판매자 적립금 지급·자동 구매 확정](full-funding-policy.md)을 따른다. 로컬 V7은 업무 테이블 30개와 새 모드를 준비한다. 아래 선금/잔금·보상·V1~V6/29개 설명은 해당 버전의 이력이다. 신규 V3 금액 명령·스케줄러의 완료를 뜻하지 않는다.

## 4. DB와 마이그레이션

V1~V6는 수정하지 않고 V7__add_full_funding_policy.sql을 추가한다. 신규 업무 테이블은 order_funds 하나로, 누적 30개다. 기존 12개 고도화 테이블은 재사용한다.

| 대상 | V7 변경 |
| --- | --- |
| orders | CONTRACT_V3 허용, 새 계약 최소 금액 CHECK |
| payments | V3/FULL 허용. 주문당 1건 UNIQUE·기존 단건 조회·전액 환불 CHECK 유지 |
| order_trade_terms | funding_mode 추가. V2 INSTALLMENT 비율 보존, V3 FULL_HOLD(전액 10000bps/보상 0/정상 수수료 1000bps/보상 수수료 0) |
| order_funds | order_id PK, payment_id UNIQUE, 최초 금액·남은 보관액·HELD/RELEASED/REFUNDED·보관/해결 시각. 결제+주문 복합 FK로 다른 주문 대금 연결 차단 |
| wallet_entries | SELLER_PAYOUT 유형과 금액 CHECK. payment/type UNIQUE·event_key UNIQUE로 중복 지급 방어 |
| order_response_timers | AUTO_CONFIRM 유형·cycle_no, 동일 납품에서도 응답 이후 새 타이머 회차 허용. 활성 한 건 UNIQUE 유지 |
| order_closures | SYSTEM/AUTO_CONFIRMED 정상 10% 수수료 분배 허용. 기존 V2 보상 종료 제약 보존 |

보관금은 결제 행을 대신하지 않는다. payments는 결제 사실, order_funds는 미해결 보관 상태, settlements/closures는 확정 결과, wallet_entries는 지갑 변동 근거다. 원래 금액·당사자·정책·타이머 납품 연결과 전체 합계는 서비스에서도 검증한다. FK/CHECK만으로 정책별 행 간 금액 관계까지 모두 보장하지 않는다.


## 이하 CONTRACT_V2 설계 이력 — 신규 V3에 적용하지 않음

### V2 설계 원문 — 이전 제약·상태

후속 합의: 현재 계약 한 개·정책별 결제 회차·관리자 타이머 해제·만료 우선 규칙을 적용한다. 실제 컬럼 타입·NULL·FK·CHECK는 백엔드의 실행 Flyway V1~V6가 기준이다. [DDL 후보](sql/advanced-db-candidate.sql)는 최초 전체 목표 검토본이고, 명령별 처리 기준은 [상태·제약 명세](advanced-db-transitions.md)를 따른다. 실제 단계별 V3~V6 적용 범위는 아래 후속 기록을 따른다.

작성일: 2026-11-05. DB 설계·컬럼·제약·인덱스·Flyway 담당: 황준범. 기능 담당자는 합의된 스키마에 맞춰 JPA·서비스·화면을 구현한다.

현재 반영 상태: main `1e10d78`에 V5/V6 나머지 업무 10개까지 반영돼 목표 신규 12개·누적 29개 업무 테이블을 제공한다. [전체 스키마 기반](advanced-schema-foundation.md)을 따른다. 기존 결제의 회차별 UNIQUE·부분 반환 전환은 서비스 연결과 함께 적용할 후속 변경으로 남긴다. 후속: §7의 outbox_events·notifications는 기존 주문 알림(#79, #80)에서 사용하며, 처리는 이벤트별 트랜잭션의 행 잠금이라 lease_owner·lease_until은 현재 비워 둔다([주문 알림](notifications.md)).

기준은 [4인 설계안](advanced-design-four-person.md)과 실행 Flyway V1/V2의 기존 17개 테이블이다. 최초 신규 20개 제안을 **12개**로 축소했다. 초기 축소안 당시에는 DB를 변경하지 않았으며, 이후 실제 V3~V6 migration을 별도 추가했다. 개인 DB에는 직접 적용하지 않았다. 본 문서가 이전 DB 초안의 테이블 분리를 대체하며 기존 2인·3인 설계 문서는 보존한다.

## 1. 축소 결과

| 기존 제안 | 변경 | 이유·한계 |
| --- | --- | --- |
| order_payment_installments | 생성하지 않음 | 고정 50%이므로 주문 총액에서 계산, 실제 지급만 payments에 보관 |
| contract_templates | 생성하지 않음 | 표준 양식은 저장소 버전 파일, 사용한 본문·버전은 계약에 고정 |
| stored_files | 계약 컬럼에 통합 | 계약당 PDF 한 개. 범용 첨부파일은 후속 |
| contract_notices | order_histories에 통합 | 기한 변경 당시 값은 이력, 알림은 outbox/notifications로 전달 |
| order_chat_rooms | 생성하지 않음 | 주문 한 건이 대화방 한 개이므로 order_id 사용 |
| admin_action_logs | 기존 업무별 이력 재사용 | 상품 로그·취소 기록·주문 이력·문의 기록으로 성공 처리 추적. 범용 통합 감사 검색은 후속 |
| topup_recovery_tasks | wallet_topups 컬럼에 통합 | 충전당 복구 작업 한 개 |
| topup_recovery_attempts | 생성하지 않음 | 마지막 결과·시도 수는 충전, 실행 이력은 Batch/구조화 로그. 모든 시도의 업무 DB 검색은 후속 |

신규 테이블은 계약 2 + 거래 조건/종료 2 + 타이머 1 + 채팅/이벤트/알림 3 + 회원 복구 2 + 운영 문의 2 = **12개**다. 기존 17개와 합쳐 업무 테이블은 29개 예상이며 선택 관심 상품과 Spring Batch 시스템 테이블은 별도다. 실제 착수 기능부터 단계적으로 생성한다.

## 2. 공통 기준

ID·금액은 signed BIGINT, 시각은 UTC DATETIME(6), 비율은 INT bps(50%=5000, 30%=3000, 10%=1000, 1%=100). 해시는 BINARY(32), 요청 UUID는 CHAR(36) ascii_bin, 상태는 VARCHAR+CHECK를 사용한다. 아래 독립 테이블은 별도 표시가 없으면 id BIGINT PK와 필요한 생성/변경 시각을 가진다. FK는 기본 RESTRICT다.

가격·소요일은 orders, 최초 신청 조건은 order_snapshots에 유지한다. 기존 주문은 LEGACY_V1, 신규는 CONTRACT_V2를 생성 시 고정한다. 신규 계약 총액은 10,000~1,000,000원이며 기존 주문에 소급 적용하지 않는다. 현재 상품 가격 하한을 일괄 변경하지 않고 신규 주문 서버 검증으로 막는다.

JSON은 계약 출력 스냅샷·이벤트 payload·이력 부가 정보에 한정한다. 금액·상태·기한처럼 검증과 검색이 필요한 핵심 데이터는 컬럼으로 둔다. FK가 행 간 합계·당사자 권한을 모두 보장하지 않으므로 서비스 검증도 수행한다.

## 3. 기존 테이블 변경

| 테이블 | 변경안 |
| --- | --- |
| members | email_verified_at NULL, auth_version BIGINT NOT NULL DEFAULT 0 |
| orders | policy_version VARCHAR(32) DEFAULT LEGACY_V1, inspection_approved_at NULL, terminated_at NULL, chat_sequence BIGINT DEFAULT 0, 새 상태 CHECK |
| order_histories | actor_type MEMBER/SYSTEM, actor_id nullable, source_event_key nullable UNIQUE, details JSON NULL, 새 action/from/to CHECK |
| cancellation_requests | resolution_source SELLER/BUYER/ADMIN nullable, 기존 resolved_by로 실제 처리자 기록 |
| payments | installment_type FULL/DEPOSIT/BALANCE, refunded_amount, PARTIALLY_REFUNDED 상태. 기존 주문 UNIQUE를 UNIQUE(order_id,installment_type)로 교체 |
| settlements | settlement_type NORMAL/NO_RESPONSE_COMPENSATION. 주문당 UNIQUE는 유지 |
| wallet_topups | recovery_attempt_count, next_recovery_at, recovery_lease_token NULL, recovery_lease_until NULL |
| wallet_entries | 기존 payment/type UNIQUE와 TOPUP 복합 FK 유지. 선금 부분 반환에 대한 연결·금액 검증 보완 |

seller_profiles·categories·products·order_snapshots·deliveries·revision_requests·wallets·reviews·product_moderation_logs는 우선 구조를 유지한다. 검색 인덱스는 쿼리 실행 계획을 보고 추가한다.

orders.paid_at은 기존 주문에서는 원결제, 신규 주문에서는 선금 지급 시각이다. 잔금 시각은 BALANCE payments에서 조회한다. buyer_confirmed_terms_at은 선금 전 확인이며 양측 계약 서명을 대체하지 않는다. paid_at·due_at·accepted_at·completed_at·terminated_at과 상태를 검사하는 기존 CHECK를 함께 검토한다.

payments는 반환액 0이면 PAID, 원결제액 미만이면 PARTIALLY_REFUNDED, 전액이면 REFUNDED다. 0<=refunded_amount<=amount와 반환 시각 조합을 검사한다. 한 결제를 여러 번 나눠 환불하는 기능은 추가하지 않는다. 기존 findByOrderId 단일 조회는 신규 정책 생성 전에 회차별 조회로 바꿔야 한다.

actor_type=SYSTEM이면 actor_id NULL, MEMBER이면 NOT NULL을 강제한다. 자동 종료에 가짜 관리자 회원을 만들지 않는다. 관리자 행동은 MEMBER와 실제 ADMIN 회원 ID·사유로 기록한다.

## 4. 거래 조건·종료: 신규 2개

### order_trade_terms

order_id PK/FK orders, deposit_rate_bps, compensation_rate_bps, normal_fee_rate_bps, compensation_fee_rate_bps, review_wait_hours, response_window_hours.

CONTRACT_V2에만 생성하고 값은 5000/3000/1000/100/48/168로 고정한다. 금액·소요일을 중복 저장하지 않는다. 선금=총액 DIV 2, 잔금=총액-선금으로 계산하고 실제 결제 시 주문 잠금 아래 회차·금액을 재검증한다. 여러 회차 자유 금액 결제를 도입할 때만 지급 계획 테이블을 검토한다.

### order_closures

order_id FK UNIQUE, reason, source_timer_id NULL FK, source_cancellation_request_id NULL FK, inquiry_id NULL FK, actor_type, actor_id NULL FK members, paid_amount, buyer_refund_amount, seller_gross_amount, fee_amount, seller_net_amount, created_at.

reason: COMPLETED/MUTUAL_CANCELLATION/ADMIN_CANCELLATION/REVIEW_NO_RESPONSE/BALANCE_UNPAID. 금전 처리 종료 결과를 주문당 한 번 고정한다. 계약 전 거절은 기존 주문 이력으로 처리한다.

CHECK: paid_amount=buyer_refund_amount+seller_gross_amount, seller_gross_amount=fee_amount+seller_net_amount, 모두 0 이상. 정상 gross는 선금+잔금, 수수료 10%. 무응답 gross는 선금의 30%, 수수료는 gross의 1%, 구매자 반환은 선금-gross다. 각각 원 미만 버림. 합의/관리자 취소는 선금 전액 반환·판매 수익/수수료 0이다.

settlements.order_id UNIQUE를 유지해 정상 정산과 보상 정산이 동시에 생기지 않게 한다. 합의/관리자 전액 반환에는 판매 수익 정산을 만들지 않는다. closure의 금액은 종료 당시 감사 근거이며 원장·정산과 같은 트랜잭션에서 일치시킨다.

## 5. 계약·서명: 신규 2개

### order_contracts

order_id FK, version INT, template_version VARCHAR(50), status VARCHAR(20), special_terms TEXT, canonical_content JSON, content_hash BINARY(32), file_storage_key NULL VARCHAR(255), file_backend NULL VARCHAR(20), file_size NULL BIGINT, file_hash NULL BINARY(32), generation_key CHAR(36), created_by FK members, signed_at NULL.

UNIQUE(order_id,version), generation_key·file_storage_key 각각 UNIQUE. 표준 본문·신청 조건·당사자 표시 이름·거래 규칙·특약을 canonical_content에 불변 출력 스냅샷으로 고정한다. 계산 기준은 orders/order_trade_terms다. 양식은 저장소의 버전별 템플릿 파일로 관리하고 기존 버전을 덮어쓰지 않는다.

READY 이후 내용·파일을 변경하지 않는다. 서명 도중 특약 변경은 새 버전 생성·기존 버전 SUPERSEDED로 처리하며 계약 성립 뒤 변경 합의는 후속이다. active_slot은 현재 버전에 1, SUPERSEDED에 NULL을 두고 UNIQUE(order_id,active_slot)로 현재 버전 한 개를 보장한다. 이전 서명 이력은 보존하지만 새 계약에 재사용하지 않는다. SIGNED는 교체하지 않는다.

READY/SIGNED에는 파일 키·크기·해시가 필수다. 구매자·판매자는 같은 저장 파일을 내려받는다. 계약당 PDF 한 개만 제공하므로 파일 컬럼을 계약에 둔다. 서명 시각은 화면 이력으로 제공하고 별도 확인 내역서 파일은 첫 범위에서 제외한다. 실제 납품 파일·복수 첨부 도입 시 파일 테이블을 재검토한다.

### contract_signatures

contract_id FK, member_id FK, party_role BUYER/SELLER, signed_content_hash BINARY(32), signed_file_hash BINARY(32), signed_at.

UNIQUE(contract_id,party_role), UNIQUE(contract_id,member_id). 서명자는 주문 당사자로 서버가 검증한다. 서명 대상의 내용·PDF 해시를 대조하고 양측 완료 시 계약을 성립시킨다. 서명 이력을 계약 상태 한 값으로 합치지 않는다.

PDF 생성은 생성 요청 DB 커밋 → 트랜잭션 밖 파일 생성 → 별도 DB 트랜잭션에서 READY 반영 순서다. generation_key로 재시도 중복을 막고 실패 후 미참조 파일을 유예 정리한다. 공개 디렉터리에 저장하지 않으며 권한 API로 다운로드한다.

결제·수정의 납기 통지는 order_histories.details에 당시 basis_at·due_at·납품 회차·계약 버전을 보존한다. outbox/notifications로 전달하고 같은 source_event_key 중복을 막는다. 최초 계약 PDF를 갱신하지 않는다.

## 6. 응답 타이머: 신규 1개

### order_response_timers

order_id FK, delivery_id FK, timer_type REVIEW_RESPONSE/BALANCE_PAYMENT, basis_at, eligible_at, started_by FK members, started_at, expires_at, status ACTIVE/CANCELLED/EXPIRED, active_slot INT NULL, ended_at NULL, ended_by NULL FK members, end_reason NULL, admin_stop_reason NULL.

판매자 클릭 시 생성한다. basis_at은 납품 시각 또는 검수 승인 시각, eligible_at=basis+48시간, expires_at=started+168시간이다. UNIQUE(order_id,timer_type,delivery_id)로 반복 시작을 막는다. UNIQUE(order_id,active_slot)와 ACTIVE일 때 1/종료 때 NULL이라는 CHECK로 주문당 활성 타이머 하나를 제한한다.

인덱스(status,expires_at,id)로 후보 조회 후 주문 잠금 아래 현재 상태·회차·유효 타이머를 재검사한다. 응답·결제 시 이전 타이머를 닫는다. EXPIRED·금액 반환·분배·종료 이력은 한 트랜잭션이다. 합의한 경계는 잠금 획득 후 서버 시각이 expires_at 이상이면 종료 우선이다. 배치가 늦어져도 구매자의 늦은 응답/결제를 허용하지 않는다.

관리자는 활성 타이머를 해제할 수 있고 처리자·시각·사유를 필수 기록한다. 해제는 거래 종료·환불과 다른 명령이다. 이미 종료·분배가 커밋됐다면 되돌리지 않는다. 해제한 같은 회차/유형 타이머의 재시작은 UNIQUE로 차단하며, 재납품이나 검수 승인으로 새 회차/유형이 생기면 별도 판단한다. 문의 접수 자체는 자동 해제하지 않는다.

## 7. 채팅·이벤트·알림: 신규 3개

| 테이블 | 핵심 컬럼 | 키·인덱스 |
| --- | --- | --- |
| order_chat_messages | order_id FK, sequence BIGINT, sender_id FK, client_message_id CHAR(36), content TEXT, created_at | UNIQUE(order_id,sequence), UNIQUE(order_id,sender_id,client_message_id) |
| outbox_events | event_id CHAR(36), type VARCHAR(60), schema_version INT, aggregate_type VARCHAR(30), aggregate_id BIGINT, payload JSON, status VARCHAR(16), attempt_count INT, next_attempt_at, lease_owner NULL, lease_until NULL, last_error_code NULL, processed_at NULL | event_id UNIQUE, (status,next_attempt_at,id) |
| notifications | recipient_id FK, event_id FK outbox_events.event_id, type VARCHAR(60), order_id NULL FK, read_at NULL, created_at | UNIQUE(recipient_id,event_id,type), 수신자+생성시각, 수신자+읽음 |

주문 자체가 방 식별자다. 메시지 본문은 1~2,000자 일반 텍스트. 주문 잠금 후 orders.chat_sequence를 증가시키고 같은 트랜잭션에서 메시지를 저장한다. 일반 MAX 조회가 기존 트랜잭션 스냅샷을 읽을 가능성을 없애기 위해 카운터 컬럼을 사용한다. 같은 주문 전송을 직렬화하며 잠금 중 소켓 통신을 하지 않는다. 실제 경합이 생기면 방/순번 테이블 분리를 검토한다.

아웃박스는 처리해야 할 이벤트, 알림은 사용자별 읽음 상태이므로 합치지 않는다. 메시지는 DB 저장 후 전달하며 Redis 장애 시에도 조회 복구가 가능해야 한다. payload에는 인증 토큰·결제 키·대화 전문을 넣지 않는다. 임대 소유권을 조건부 검증해 오래된 작업의 완료 덮어쓰기를 막는다. 알림 FK가 있는 이벤트를 무조건 삭제하지 않는다.

## 8. 회원 복구: 신규 2개

| 테이블 | 핵심 컬럼 | 제약 |
| --- | --- | --- |
| account_challenges | purpose VARCHAR(24), member_id NULL FK, email VARCHAR(254), token_hash BINARY(32), credential_version NULL BIGINT, issued_at, expires_at, verified_at NULL, consumed_at NULL, delivery_status VARCHAR(16), last_error_code NULL | token_hash UNIQUE, 목적+이메일+발급시각, 만료시각 |
| auth_request_limits | scope VARCHAR(30), subject_hmac BINARY(32), window_start, request_count INT, expires_at | UNIQUE(scope,subject_hmac,window_start), 만료시각 |

목적은 SIGNUP_EMAIL/EXISTING_EMAIL/PASSWORD_RESET이다. 가입 전 member_id는 없고 재설정에는 member_id·발급 당시 인증 버전이 필수다. 고엔트로피 원문 토큰은 저장하지 않고 해시만 저장한다. 인증 증명 소비·회원·지갑·최초 지급을 원자 처리한다.

인증 요청 제한은 비회원에게도 필요하므로 members에 넣지 않는다. Redis로 제한을 전환하면 이 테이블을 생략할 수 있으나 초기 회원 복구가 Redis 장애/미도입에 의존하지 않도록 우선 유지한다. 메일 전송은 DB 트랜잭션 밖에서 실행한다.

## 9. 운영 문의: 신규 2개

| 테이블 | 핵심 컬럼 | 제약 |
| --- | --- | --- |
| support_inquiries | requester_id FK, order_id NULL FK, cancellation_request_id NULL FK, type VARCHAR(30), status VARCHAR(20), subject VARCHAR(150), assigned_admin_id NULL FK, resolved_at NULL | 신청자+생성시각, 상태+생성시각 |
| support_messages | inquiry_id FK, sender_id FK, client_message_id CHAR(36), content TEXT, created_at | UNIQUE(inquiry_id,sender_id,client_message_id), 문의+ID |

취소 운영 문의는 주문·취소 요청이 필수이고 본인 주문인지 검증한다. 일반 문의는 NULL 가능하다. 문의 종료와 주문 종료를 구분한다. 관리자 승인 시 cancellation_requests.resolved_by·resolution_source=ADMIN과 order_histories의 사유·actor를 기록한다.

관리자 작업 성공 이력은 상품 운영 로그·취소 처리·종료 결과·주문 이력·문의 기록으로 추적한다. 기록은 업무 변경과 같은 트랜잭션에 저장한다. 실패 시도·조회·복구 실행 오류는 안전한 구조화 로그로 남긴다. 범용 관리자 감사 테이블과 전체 시도의 DB 검색은 후속이다. 문의 접수만으로 타이머를 자동 중단하지 않고 관리자가 별도 해제 명령을 실행한다.

## 10. 복구·상품 기능은 기존 테이블 우선

충전 복구는 wallet_topups의 시도 수·다음 시각·임대 토큰·만료·마지막 오류를 사용한다. 조회 인덱스(status,next_recovery_at,id)를 검토한다. 외부 조회는 트랜잭션 밖, 최종 지급은 기존 충전 잠금·원장 UNIQUE로 검증한다. 별도 시도 원문 DB 이력은 만들지 않는다.

Spring Batch 실행 메타데이터는 선택 버전의 공식 BATCH_* 스키마를 별도로 적용한다. 이를 임의로 없애거나 업무 테이블에 합치지 않는다. 신규 업무 12개 수에는 포함하지 않으며 정확한 시스템 테이블 수는 도입 시 확인한다.

상품 필터·판매자 공개 소개·후기 요약은 기존 테이블 조회로 구현한다. 관심 상품을 실제 선택하면 product_favorites(member_id,product_id,created_at), 복합 PK를 추가한다. 캐시와 통계 집계 테이블을 미리 만들지 않는다.

## 11. 상태·일관성·마이그레이션

기존 상태를 유지하고 CONTRACT_V2에 AWAITING_CONTRACT·AWAITING_BALANCE·TERMINATED를 추가하는 안이다. 서명 후 선금 대기 → 선금 후 진행 → 납품 후 검수 → 검수 승인 후 잔금 대기 → 잔금 결제와 COMPLETED를 원자 처리한다. 무응답 분배는 TERMINATED, 합의/관리자 취소는 CANCELLED다. 정상 완료만 후기를 허용한다.

잔금 결제·구매 확정·정산·closure·원장·이력·outbox를 한 트랜잭션으로, 자동 종료도 타이머·선금 반환·보상 정산·closure·원장·이력·outbox를 한 트랜잭션으로 처리한다. 서로 다른 종료가 함께 성공하면 안 된다. 기존 회원→주문→지갑 잠금 순서에 하위 계약·타이머·취소 요청 획득 순서를 맞추고 호출 그래프로 재검토한다.

새 Flyway는 적용 시 최신 번호부터 기능별로 추가한다. 기존 V1/V2는 수정하지 않는다. 실제 순서는 V3 금융 컬럼 → V4 계약/서명 → V5 소통·회원·문의 → V6 거래 조건·타이머·종료다. 선택 관심 상품·Batch 시스템 스키마는 실제 착수 시 추가한다.

기존 주문은 LEGACY_V1, 결제는 FULL, 환불액은 기존 상태에 따라 0/전액, 정산은 NORMAL, 이력 actor_type은 MEMBER로 백필한다. 기존 회원 인증 시각은 NULL이다. DDL의 실패를 업무 트랜잭션처럼 전체 롤백 가능하다고 가정하지 않는다. 확장·백필·제약 교체·호환 코드·신규 정책 활성화를 격리 MySQL 8.4에서 검증한다.

초기 설계 이후 실제 V3~V6 DDL과 기존 엔티티 호환 변경을 추가했다. 다음 단계는 정책별 상태·서비스·조회 연결 및 회차별 결제 제약의 공동 전환이다. 실행 결과는 전체 스키마·정책 경계 기록에서 확인하며, 개인 DB에 직접 적용한 것으로 표현하지 않는다.
