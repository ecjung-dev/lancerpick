# 관리자 작업 감사 이력 설계안 — K6 재처리 실행 선행

2026-11-09, 김창재. **설계 제안이며 구현·DDL·Flyway는 없다.** 테이블 생성은 황준범 담당이고, 아래 DDL 초안은 협의용이다. 이 문서가 확정되기 전에는 관리자 재처리 실행 버튼·API를 추가하지 않는다.

## 1. 왜 필요한가

[금융 조회 연결 계약](admin-finance-support.md#k6-실제-실행을-연결할-때)은 관리자 재처리 실행 전에 **감사 요청 기록을 먼저 커밋**하고, 외부 조회·Batch 시작은 그 트랜잭션 밖에서 실행하며, 결과를 별도로 커밋하도록 정했다. [PDF 운영 조회](admin-contract-pdf-inspection.md)도 재생성 실행을 감사 이력 계약 뒤로 미뤘다.

현재 관리자 기록은 업무별로 나뉘어 있다.

| 기존 관리자 명령 | 이미 남는 기록 | 이번 설계에서 |
| --- | --- | --- |
| 상품 숨김/복구(K2) | `product_moderation_logs` | 그대로 둔다 |
| 문의 승인·거절·닫기(K4) | 취소 요청 처리 이력·주문 이력·문의 처리자 | 그대로 둔다 |
| 타이머 해제(K5) | 타이머 `ADMIN_STOP`·주문 이력 | 그대로 둔다 |
| 회원 제재(B2) | `member_sanctions` | 그대로 둔다 |
| **충전 재확인·Batch 재시작·PDF 재생성(K6)** | **없음** | **새 감사 이력 대상** |

[고도화 DB 설계](advanced-db-design.md)의 "기존 업무별 이력 재사용" 결정은 유지한다. 위 기존 명령은 성공 결과가 이미 업무 기록에 남는다. 그래서 같은 내용을 두 번 쓰지 않는다. K6 재처리는 실행 결과가 외부 응답·Batch 실행에 달려 있어 업무 기록만으로는 "누가, 왜, 언제 요청했고 결과가 무엇이었는지"를 알 수 없다. 이 공백만 새 테이블로 채운다.

## 2. 범위

- 대상 명령 3개: `TOPUP_RECHECK`(미확정 충전 단건 재확인), `TOPUP_RECOVERY_RESTART`(실패/중지된 복구 Batch 재시작), `CONTRACT_PDF_REGENERATE`(계약 PDF 생성 재시도).
- 범위 밖: 잔액 직접 수정, 실패 상태 강제 완료, 시도 수 초기화, 알림 `FAILED` 재발송(별도 설계), 민감 원문 열람.

## 3. 테이블 초안 `admin_action_logs` (DDL은 황준범과 확정)

V10의 `member_sanctions`, V1의 `product_moderation_logs`와 같은 규칙(UTC DATETIME(6), 요청 UUID CHAR(36) ascii_bin, VARCHAR+CHECK, FK RESTRICT)을 따른다.

```sql
-- 협의용 초안. 실행하지 않는다. 실제 마이그레이션 번호·이름은 황준범이 정한다.
CREATE TABLE admin_action_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_id BIGINT NOT NULL,                 -- 세션의 관리자. 요청 본문 값을 믿지 않는다
    action VARCHAR(32) NOT NULL,
    target_type VARCHAR(24) NOT NULL,
    target_id BIGINT NOT NULL,                -- 충전 ID / Batch 실행 ID / 계약 ID
    order_id BIGINT NULL,                     -- 주문으로 찾기 위한 보조 값(PDF)
    reason VARCHAR(1000) NOT NULL,
    request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) NOT NULL,              -- REQUESTED / SUCCEEDED / REJECTED / FAILED / UNKNOWN
    result_code VARCHAR(64) NULL,             -- 업무 결과·오류 코드만. 예외 원문·결제 키 금지
    requested_at DATETIME(6) NOT NULL,
    lease_until DATETIME(6) NOT NULL,         -- 이 시각까지 결과가 없으면 "결과 확인 필요"
    completed_at DATETIME(6) NULL,
    in_progress_key VARCHAR(80) GENERATED ALWAYS AS (
      CASE WHEN status = 'REQUESTED' THEN CONCAT(action, ':', target_type, ':', target_id) END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_action_request (admin_id, request_key),
    UNIQUE KEY uk_admin_action_in_progress (in_progress_key),
    KEY ix_admin_action_target (target_type, target_id, id),
    KEY ix_admin_action_order (order_id, id),
    KEY ix_admin_action_admin (admin_id, id),
    CONSTRAINT fk_admin_action_admin FOREIGN KEY (admin_id) REFERENCES members(id),
    CONSTRAINT ck_admin_action_action CHECK (action IN ('TOPUP_RECHECK','TOPUP_RECOVERY_RESTART','CONTRACT_PDF_REGENERATE')),
    CONSTRAINT ck_admin_action_target CHECK (target_type IN ('WALLET_TOPUP','BATCH_JOB_EXECUTION','CONTRACT')),
    CONSTRAINT ck_admin_action_status CHECK (status IN ('REQUESTED','SUCCEEDED','REJECTED','FAILED','UNKNOWN')),
    CONSTRAINT ck_admin_action_reason CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 1000),
    CONSTRAINT ck_admin_action_done CHECK (
      (status = 'REQUESTED' AND completed_at IS NULL) OR
      (status <> 'REQUESTED' AND completed_at IS NOT NULL AND completed_at >= requested_at)),
    CONSTRAINT ck_admin_action_lease CHECK (lease_until > requested_at)
) ENGINE=InnoDB;
```

- `target_id`는 대상별 테이블이 달라 FK를 두지 않는다(Batch 실행 ID는 시스템 테이블). 대신 서비스가 대상 존재를 확인한 뒤에만 기록한다.
- `uk_admin_action_in_progress`: 같은 대상·같은 명령의 `REQUESTED`는 DB에서 하나만 허용한다. 끝난 기록은 NULL이 되어 여러 개 남을 수 있다. 서로 다른 관리자의 동시 클릭도 여기서 막힌다.
- `uk_admin_action_request`: 같은 관리자가 같은 요청 키로 다시 보내면(응답 유실 후 재시도) 새 기록을 만들지 않고 기존 기록을 돌려준다.

## 4. 실행 순서 (명령 공통)

1. **요청 기록 트랜잭션**(짧은 READ_COMMITTED): 관리자 회원 공유 잠금 → DB의 ADMIN 역할 재확인 → 사유 정규화(앞뒤 공백만 제거, 1~1,000자) → 대상 존재·후보 판단(기존 `AdminFinanceQueryService`·`AdminContractPdfQueryService`의 판단 재사용) → `REQUESTED` 저장 후 커밋.
   - 같은 `(admin_id, request_key)`가 있으면 인자가 같을 때 기존 기록 반환, 다르면 409 `ADMIN_ACTION_REQUEST_CONFLICT`.
   - 진행 중 기록이 있으면 409 `ADMIN_ACTION_IN_PROGRESS`. 단, 그 기록의 `lease_until`이 지났으면 같은 트랜잭션에서 그 기록을 `UNKNOWN`(result_code `LEASE_EXPIRED`)으로 닫고 새 요청을 받는다.
   - 후보가 아니면 기록 없이 409 `ADMIN_ACTION_NOT_ELIGIBLE`(사유 코드 포함).
2. **실행**(요청 기록 트랜잭션 밖): 각 담당의 기존 업무 서비스를 호출한다. 업무 서비스가 자기 잠금·처리권·상태를 다시 검사한다. 관리자 코드가 잔액·상태를 직접 바꾸지 않는다.
3. **결과 기록 트랜잭션**: `status='REQUESTED' AND id=?` 조건으로만 결과를 쓴다(이미 `UNKNOWN`으로 닫혔으면 덮지 않는다).
   - 성공 `SUCCEEDED`, 업무 규칙 거절 `REJECTED`(예: 그사이 다른 처리 완료), 확실한 실패 `FAILED`, 통신 오류·시간 초과·결과 불명확 `UNKNOWN`.
   - `UNKNOWN`을 성공·실패로 단정하지 않는다. 화면은 "결과 확인 필요"로 보여 주고 대상 조회로 실제 상태를 확인하게 한다.
4. 1과 3 사이에 서버가 멈추면 기록은 `REQUESTED`로 남는다. `lease_until`이 지나면 다음 요청이 1단계에서 정리한다. 자동 재실행은 하지 않는다.

`lease_until` 길이는 명령별로 정한다(예: 충전 재확인은 토스 조회 제한 시간 + 여유, PDF 재생성은 `contracts.generation-timeout`). 실제 값은 각 서비스 담당과 맞춘다.

## 5. API·화면 초안

| 메서드·경로 | 본문 | 응답 |
| --- | --- | --- |
| POST `/api/admin/finance/topups/{id}/recheck` | `reason`, `requestKey`(UUID) | 기록 + 대상 최신 상태 |
| POST `/api/admin/finance/recovery-jobs/{executionId}/restart` | `reason`, `requestKey` | 기록 |
| POST `/api/admin/orders/{orderId}/contract-pdf/regenerate` | `reason`, `requestKey`, `version` | 기록 + PDF 최신 상태 |
| GET `/api/admin/action-logs` | `action`, `targetType`, `targetId`, `orderId`, `page` | 목록(최신순) |

- 공통: `/api/admin/**` ADMIN, CSRF, `Cache-Control: no-store`, 관리자 ID는 `CurrentMemberId.require(authentication)`. 일반 회원 403, 세션 없음 401.
- 응답 상태: 새 기록 202(결과가 `REQUESTED`/`UNKNOWN`일 수 있음), 같은 요청 키 재전송은 200.
- 화면: 기존 `/admin/finance` 후보 칸에 "재처리" 버튼과 사유 입력을 붙인다. 버튼을 누를 때 `requestKey`를 한 번 만들고, 결과가 불명확해 다시 누르면 같은 키를 쓴다. 계정 전환 보호(`captureAccountScope`)·자동 재시도 금지·두 번 클릭 방지는 기존 관리자 명령과 같다. 대상 상세에 "관리자 작업 이력" 칸을 둔다.
- 응답·로그 금지 항목: 결제 키, 토스 응답 원문, 예외 메시지, 파일 경로·해시, 처리권 키, 회원 이메일.

## 6. 정할 것 (담당)

1. 테이블·인덱스·생성 열 방식 확정과 마이그레이션 작성 — 황준범.
2. 명령별 업무 서비스 진입점(관리자 실행용)과 결과 코드 목록 — 충전 재확인·Batch 재시작·PDF 재생성 모두 황준범 영역. 기존 구매자 API에 타인 ID를 넣어 호출하지 않는다.
3. `lease_until` 길이와 PDF 재생성 시 주문 잠금 순서 — 황준범·정은찬.
4. 기존 관리자 명령(K2·K4·K5·B2)도 이 테이블에 함께 남길지 — 이번에는 남기지 않는 것을 제안. 통합 검색이 필요해지면 후속으로 검토.
5. 감사 이력 보관 기간·삭제 정책 — 팀 결정(기본은 삭제하지 않음).

## 7. 검증 계획 (구현 시)

- 일반 회원·강등된 관리자 차단, 사유·요청 키 검증 400.
- 같은 요청 키 재전송 시 기록 1개, 다른 인자 409.
- 두 관리자 동시 요청 시 `REQUESTED` 1개, 나머지 409.
- 실행 중 서버 중단(REQUESTED 잔존) → 기한 후 새 요청이 `UNKNOWN`으로 정리.
- 반복 재처리에도 중복 지급·중복 PDF 생성 없음(업무 서비스의 기존 멱등 검사 재사용).
- 응답·로그에 금지 항목이 없음.
