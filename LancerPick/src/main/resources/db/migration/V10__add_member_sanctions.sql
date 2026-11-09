CREATE TABLE member_sanctions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    kind VARCHAR(24) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    applied_by BIGINT NOT NULL,
    request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    revoked_by BIGINT NULL,
    revoke_reason VARCHAR(1000) NULL,
    timer_stop_delivered_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_sanction_request (member_id, request_key),
    KEY ix_member_sanction_current (member_id, kind, id),
    KEY ix_member_sanction_delivery (kind, timer_stop_delivered_at, id),
    CONSTRAINT fk_member_sanction_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_member_sanction_admin FOREIGN KEY (applied_by) REFERENCES members(id),
    CONSTRAINT fk_member_sanction_revoker FOREIGN KEY (revoked_by) REFERENCES members(id),
    CONSTRAINT ck_member_sanction_kind CHECK (kind IN ('GENERAL_RESTRICTION','SECURITY_LOCK')),
    CONSTRAINT ck_member_sanction_time CHECK (ends_at > starts_at),
    CONSTRAINT ck_member_sanction_revoke CHECK (
      (revoked_at IS NULL AND revoked_by IS NULL AND revoke_reason IS NULL) OR
      (revoked_at IS NOT NULL AND revoked_at >= starts_at AND revoked_by IS NOT NULL AND revoke_reason IS NOT NULL)),
    CONSTRAINT ck_member_sanction_delivery CHECK (
      timer_stop_delivered_at IS NULL OR (kind='SECURITY_LOCK' AND timer_stop_delivered_at >= starts_at))
) ENGINE=InnoDB;
