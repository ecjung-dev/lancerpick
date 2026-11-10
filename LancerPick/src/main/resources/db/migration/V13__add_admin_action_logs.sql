-- K6 요청은 업무 실행 전에 커밋한다. 금액·PDF·Batch 상태의 실행 원장은 각 업무에 유지한다.
CREATE TABLE admin_action_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    target_type VARCHAR(24) NOT NULL,
    target_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    reason VARCHAR(1000) NOT NULL,
    request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) NOT NULL,
    result_code VARCHAR(64) NULL,
    requested_at DATETIME(6) NOT NULL,
    lease_until DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    in_progress_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin
      GENERATED ALWAYS AS (
        CASE WHEN status = 'REQUESTED' THEN CONCAT(action, ':', target_type, ':', target_id) END
      ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_action_request (admin_id, request_key),
    UNIQUE KEY uk_admin_action_in_progress (in_progress_key),
    KEY ix_admin_action_target (target_type, target_id, id),
    KEY ix_admin_action_order (order_id, id),
    KEY ix_admin_action_admin (admin_id, id),
    CONSTRAINT fk_admin_action_admin FOREIGN KEY (admin_id) REFERENCES members(id) ON DELETE RESTRICT,
    CONSTRAINT fk_admin_action_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    CONSTRAINT ck_admin_action_target_id CHECK (target_id > 0),
    CONSTRAINT ck_admin_action_pair CHECK (
      (action = 'TOPUP_RECHECK' AND target_type = 'WALLET_TOPUP' AND order_id IS NULL) OR
      (action = 'TOPUP_RECOVERY_RESTART' AND target_type = 'BATCH_JOB_EXECUTION' AND order_id IS NULL) OR
      (action = 'CONTRACT_PDF_REGENERATE' AND target_type = 'CONTRACT' AND order_id IS NOT NULL)),
    CONSTRAINT ck_admin_action_status CHECK (status IN ('REQUESTED','SUCCEEDED','REJECTED','FAILED','UNKNOWN')),
    CONSTRAINT ck_admin_action_reason CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 1000),
    CONSTRAINT ck_admin_action_done CHECK (
      (status = 'REQUESTED' AND completed_at IS NULL AND result_code IS NULL) OR
      (status <> 'REQUESTED' AND completed_at IS NOT NULL AND completed_at >= requested_at AND result_code IS NOT NULL)),
    CONSTRAINT ck_admin_action_lease CHECK (lease_until > requested_at)
) ENGINE=InnoDB;
