-- Toss test top-up preparation. V1 stays immutable; this migration does not grant credits.
CREATE TABLE wallet_topups (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '충전 신청 ID',
    member_id BIGINT NOT NULL COMMENT '충전 대상 지갑 회원 ID',
    toss_order_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '서버 생성 토스 주문번호, 상품 주문 ID와 별개',
    amount BIGINT NOT NULL COMMENT '서버에서 확정한 충전 금액 원',
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'KRW',
    is_test BOOLEAN NOT NULL COMMENT '테스트 결제만 허용',
    payment_key VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '외부 결제 식별자, 승인 시도 전에는 NULL',
    status VARCHAR(20) COLLATE utf8mb4_0900_as_cs NOT NULL COMMENT '내부 충전 처리 상태, 토스 상태와 별개',
    approval_request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '서버 생성 승인 멱등키 UUID, 재확인 때 유지',
    last_error_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '안전한 오류 코드만 저장, 원문 응답 제외',
    created_at DATETIME(6) NOT NULL COMMENT '신청 시각 UTC',
    updated_at DATETIME(6) NOT NULL COMMENT '상태 변경 시각 UTC',
    processing_started_at DATETIME(6) NULL COMMENT '승인 처리 시작 시각 UTC, 정체된 요청 재확인 기준',
    approved_at DATETIME(6) NULL COMMENT '검증한 토스 승인 시각 UTC',
    completed_at DATETIME(6) NULL COMMENT '내부 적립금 반영 완료 시각 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_topups_toss_order (toss_order_id),
    UNIQUE KEY uk_wallet_topups_payment_key (payment_key),
    UNIQUE KEY uk_wallet_topups_approval_request (approval_request_key),
    -- Composite FK below prevents crediting another member or a different amount.
    UNIQUE KEY uk_wallet_topups_credit_reference (id, member_id, amount),
    KEY ix_wallet_topups_member_created (member_id, created_at DESC, id DESC),
    KEY ix_wallet_topups_status_updated (status, updated_at, id),
    CONSTRAINT fk_wallet_topups_wallet FOREIGN KEY (member_id) REFERENCES wallets (member_id),
    CONSTRAINT ck_wallet_topups_amount CHECK (amount BETWEEN 1 AND 1000000),
    CONSTRAINT ck_wallet_topups_currency CHECK (currency = 'KRW'),
    CONSTRAINT ck_wallet_topups_test CHECK (is_test = 1),
    CONSTRAINT ck_wallet_topups_order CHECK (REGEXP_LIKE(toss_order_id, '^[A-Za-z0-9_=-]{6,64}$', 'c')),
    CONSTRAINT ck_wallet_topups_payment_key CHECK (payment_key IS NULL OR CHAR_LENGTH(TRIM(payment_key)) BETWEEN 1 AND 200),
    CONSTRAINT ck_wallet_topups_request_key CHECK (
        REGEXP_LIKE(approval_request_key, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT ck_wallet_topups_status CHECK (status IN ('READY', 'PROCESSING', 'UNKNOWN', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_wallet_topups_processing CHECK (
        (status = 'READY' AND payment_key IS NULL AND processing_started_at IS NULL)
        OR (status IN ('PROCESSING', 'UNKNOWN', 'COMPLETED') AND payment_key IS NOT NULL AND processing_started_at IS NOT NULL)
        OR status = 'FAILED'),
    CONSTRAINT ck_wallet_topups_completion CHECK (
        (status = 'COMPLETED' AND approved_at IS NOT NULL AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND approved_at IS NULL AND completed_at IS NULL)),
    CONSTRAINT ck_wallet_topups_time CHECK (
        updated_at >= created_at
        AND (processing_started_at IS NULL OR (processing_started_at >= created_at AND processing_started_at <= updated_at))
        AND (approved_at IS NULL OR (approved_at >= created_at AND approved_at <= completed_at))
        AND (completed_at IS NULL OR (completed_at >= processing_started_at AND completed_at <= updated_at)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE wallet_entries
    ADD COLUMN topup_id BIGINT NULL COMMENT '충전 신청 ID, TOPUP 유형만 사용' AFTER payment_id,
    MODIFY COLUMN type VARCHAR(16) COLLATE utf8mb4_0900_as_cs NOT NULL COMMENT '초기 지급, 결제, 환불, 외부 테스트 충전',
    DROP CHECK ck_wallet_entries_type,
    DROP CHECK ck_wallet_entries_amount,
    ADD UNIQUE KEY uk_wallet_entries_topup (topup_id),
    ADD CONSTRAINT fk_wallet_entries_topup FOREIGN KEY (topup_id, member_id, amount_delta)
        REFERENCES wallet_topups (id, member_id, amount),
    ADD CONSTRAINT ck_wallet_entries_type CHECK (type IN ('INITIAL_GRANT', 'PAYMENT', 'REFUND', 'TOPUP')),
    ADD CONSTRAINT ck_wallet_entries_amount CHECK (
        (type = 'INITIAL_GRANT' AND payment_id IS NULL AND topup_id IS NULL AND amount_delta = 1000000)
        OR (type = 'PAYMENT' AND payment_id IS NOT NULL AND topup_id IS NULL AND amount_delta BETWEEN -1000000 AND -1)
        OR (type = 'REFUND' AND payment_id IS NOT NULL AND topup_id IS NULL AND amount_delta BETWEEN 1 AND 1000000)
        OR (type = 'TOPUP' AND payment_id IS NULL AND topup_id IS NOT NULL AND amount_delta BETWEEN 1 AND 1000000));
