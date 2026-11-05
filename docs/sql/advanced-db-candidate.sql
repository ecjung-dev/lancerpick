-- HISTORICAL CONTRACT_V2 REVIEW CANDIDATE ONLY. Never apply on a V3+ runtime database.
-- CONTRACT_V3 full funding is defined by V7__add_full_funding_policy.sql and docs/full-funding-policy.md.
-- This preserved installment proposal is for isolated V1/V2 regression review only.

-- Not a runtime Flyway migration. Requires coordinated entity/service changes.

-- Existing orders remain LEGACY_V1. No database creation or data deletion.

SET NAMES utf8mb4;

ALTER TABLE members ADD email_verified_at DATETIME(6) NULL,
 ADD auth_version BIGINT NOT NULL DEFAULT 0,
 ADD CONSTRAINT ck_members_auth_version CHECK (auth_version >= 0);
ALTER TABLE orders ADD policy_version VARCHAR(32) NOT NULL DEFAULT 'LEGACY_V1',
 ADD inspection_approved_at DATETIME(6) NULL, ADD terminated_at DATETIME(6) NULL,
 ADD chat_sequence BIGINT NOT NULL DEFAULT 0,
 ADD UNIQUE KEY uk_orders_id_policy (id,policy_version),
 DROP CHECK ck_orders_status, DROP CHECK ck_orders_acceptance,
 DROP CHECK ck_orders_paid_status, DROP CHECK ck_orders_unpaid_status;

ALTER TABLE orders
 ADD CONSTRAINT ck_orders_status CHECK (status IN ('REQUESTED','AWAITING_PAYMENT','IN_PROGRESS','AWAITING_CONFIRMATION','REVISION_IN_PROGRESS','COMPLETED','REJECTED','CANCELLED','AWAITING_CONTRACT','AWAITING_BALANCE','TERMINATED')),
 ADD CONSTRAINT ck_orders_policy CHECK (policy_version IN ('LEGACY_V1','CONTRACT_V2')),
 ADD CONSTRAINT ck_orders_v2_price CHECK (policy_version <> 'CONTRACT_V2' OR price_amount >= 10000),
 ADD CONSTRAINT ck_orders_legacy_states CHECK (policy_version <> 'LEGACY_V1' OR status NOT IN ('AWAITING_CONTRACT','AWAITING_BALANCE','TERMINATED')),
 ADD CONSTRAINT ck_orders_acceptance CHECK (
  (status IN ('REQUESTED','REJECTED') AND accepted_at IS NULL)
  OR (status IN ('AWAITING_CONTRACT','AWAITING_PAYMENT','IN_PROGRESS','AWAITING_CONFIRMATION','REVISION_IN_PROGRESS','AWAITING_BALANCE','COMPLETED','TERMINATED') AND accepted_at IS NOT NULL)
  OR status='CANCELLED'),
 ADD CONSTRAINT ck_orders_paid_status CHECK (status NOT IN ('IN_PROGRESS','AWAITING_CONFIRMATION','REVISION_IN_PROGRESS','AWAITING_BALANCE','COMPLETED','TERMINATED') OR paid_at IS NOT NULL),
 ADD CONSTRAINT ck_orders_unpaid_status CHECK (status NOT IN ('REQUESTED','AWAITING_CONTRACT','AWAITING_PAYMENT','REJECTED') OR paid_at IS NULL),
 ADD CONSTRAINT ck_orders_terminated CHECK ((status='TERMINATED' AND terminated_at IS NOT NULL) OR (status<>'TERMINATED' AND terminated_at IS NULL)),
 ADD CONSTRAINT ck_orders_inspection CHECK ((inspection_approved_at IS NULL OR (paid_at IS NOT NULL AND inspection_approved_at >= paid_at AND updated_at >= inspection_approved_at)) AND (status<>'AWAITING_BALANCE' OR inspection_approved_at IS NOT NULL)),
 ADD CONSTRAINT ck_orders_terminated_time CHECK (terminated_at IS NULL OR (paid_at IS NOT NULL AND terminated_at >= paid_at AND updated_at >= terminated_at)),
 ADD CONSTRAINT ck_orders_chat_sequence CHECK (chat_sequence >= 0);

ALTER TABLE payments
 ADD order_policy_version VARCHAR(32) NOT NULL DEFAULT 'LEGACY_V1',
 ADD installment_type VARCHAR(16) NOT NULL DEFAULT 'FULL',
 ADD refunded_amount BIGINT NOT NULL DEFAULT 0;
UPDATE payments SET refunded_amount=amount WHERE status='REFUNDED';
ALTER TABLE payments DROP INDEX uk_payments_order,
 ADD UNIQUE KEY uk_payments_order_installment (order_id,installment_type),
 ADD CONSTRAINT fk_payments_order_policy FOREIGN KEY (order_id,order_policy_version) REFERENCES orders(id,policy_version),
 DROP CHECK ck_payments_status, DROP CHECK ck_payments_refund,
 ADD CONSTRAINT ck_payments_installment_policy CHECK (
 (order_policy_version='LEGACY_V1' AND installment_type='FULL') OR
 (order_policy_version='CONTRACT_V2' AND installment_type IN ('DEPOSIT','BALANCE'))),
 ADD CONSTRAINT ck_payments_refund_amount CHECK (refunded_amount BETWEEN 0 AND amount),
 ADD CONSTRAINT ck_payments_refund_state CHECK (
 (status='PAID' AND refunded_amount=0 AND refunded_at IS NULL) OR
 (status='PARTIALLY_REFUNDED' AND refunded_amount>0 AND refunded_amount<amount AND refunded_at IS NOT NULL) OR
 (status='REFUNDED' AND refunded_amount=amount AND refunded_at IS NOT NULL));
ALTER TABLE payments MODIFY status VARCHAR(20) COLLATE utf8mb4_0900_as_cs NOT NULL DEFAULT 'PAID';
ALTER TABLE settlements ADD settlement_type VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
 ADD CONSTRAINT ck_settlements_kind CHECK ((settlement_type='NORMAL' AND fee_rate_bps=1000) OR (settlement_type='NO_RESPONSE_COMPENSATION' AND fee_rate_bps=100));
ALTER TABLE cancellation_requests ADD resolution_source VARCHAR(10) NULL,
 ADD UNIQUE KEY uk_cancellation_id_order (id,order_id);
UPDATE cancellation_requests SET resolution_source=CASE WHEN status='WITHDRAWN' THEN 'BUYER' ELSE 'SELLER' END WHERE status<>'PENDING';
ALTER TABLE cancellation_requests ADD CONSTRAINT ck_cancellation_source CHECK (
 (status='PENDING' AND resolution_source IS NULL) OR
 (status='WITHDRAWN' AND resolution_source IS NOT NULL AND resolution_source='BUYER') OR
 (status IN ('APPROVED','REJECTED') AND resolution_source IS NOT NULL AND resolution_source IN ('SELLER','ADMIN')));
ALTER TABLE deliveries ADD UNIQUE KEY uk_deliveries_id_order (id,order_id);

ALTER TABLE order_histories MODIFY actor_id BIGINT NULL,
 ADD actor_type VARCHAR(10) NOT NULL DEFAULT 'MEMBER', ADD source_event_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD details JSON NULL, ADD UNIQUE KEY uk_order_history_source (source_event_key),
 DROP CHECK ck_order_histories_action, DROP CHECK ck_order_histories_from, DROP CHECK ck_order_histories_to,
 ADD CONSTRAINT ck_order_history_actor CHECK ((actor_type='MEMBER' AND actor_id IS NOT NULL) OR (actor_type='SYSTEM' AND actor_id IS NULL)),
 ADD CONSTRAINT ck_order_histories_action CHECK (action IN ('CREATED','ACCEPTED','REJECTED','PAID','DELIVERED','REVISION_REQUESTED','CANCELLATION_REQUESTED','CANCELLATION_APPROVED','CANCELLATION_REJECTED','CANCELLATION_WITHDRAWN','CANCELLED','COMPLETED','CONTRACT_CREATED','CONTRACT_SIGNED','DEPOSIT_PAID','INSPECTION_APPROVED','BALANCE_PAID','TIMER_STARTED','TIMER_CANCELLED','TIMER_ADMIN_STOPPED','TERMINATED','DEADLINE_NOTIFIED')),
 ADD CONSTRAINT ck_order_histories_from CHECK (from_status IS NULL OR from_status IN ('REQUESTED','AWAITING_PAYMENT','IN_PROGRESS','AWAITING_CONFIRMATION','REVISION_IN_PROGRESS','COMPLETED','REJECTED','CANCELLED','AWAITING_CONTRACT','AWAITING_BALANCE','TERMINATED')),
 ADD CONSTRAINT ck_order_histories_to CHECK (to_status IN ('REQUESTED','AWAITING_PAYMENT','IN_PROGRESS','AWAITING_CONFIRMATION','REVISION_IN_PROGRESS','COMPLETED','REJECTED','CANCELLED','AWAITING_CONTRACT','AWAITING_BALANCE','TERMINATED'));
ALTER TABLE wallet_topups ADD recovery_attempt_count INT NOT NULL DEFAULT 0,
 ADD next_recovery_at DATETIME(6) NULL, ADD recovery_lease_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD recovery_lease_until DATETIME(6) NULL,
 ADD KEY ix_topup_recovery (status,next_recovery_at,id),
 ADD CONSTRAINT ck_topup_recovery_count CHECK (recovery_attempt_count>=0),
 ADD CONSTRAINT ck_topup_recovery_lease CHECK ((recovery_lease_token IS NULL AND recovery_lease_until IS NULL) OR (recovery_lease_token IS NOT NULL AND recovery_lease_until IS NOT NULL));

CREATE TABLE order_trade_terms (
    order_id BIGINT NOT NULL PRIMARY KEY,
    deposit_rate_bps INT NOT NULL,
    compensation_rate_bps INT NOT NULL,
    normal_fee_rate_bps INT NOT NULL,
    compensation_fee_rate_bps INT NOT NULL,
    review_wait_hours INT NOT NULL,
    response_window_hours INT NOT NULL,
    CONSTRAINT fk_trade_terms_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT ck_trade_terms_v2 CHECK (deposit_rate_bps=5000 AND compensation_rate_bps=3000 AND normal_fee_rate_bps=1000 AND compensation_fee_rate_bps=100 AND review_wait_hours=48 AND response_window_hours=168)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE order_contracts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    version INT NOT NULL,
    template_version VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    active_slot INT NULL,
    special_terms TEXT NOT NULL,
    canonical_content JSON NOT NULL,
    content_hash BINARY(32) NOT NULL,
    file_storage_key VARCHAR(255) NULL,
    file_backend VARCHAR(20) NULL,
    file_size BIGINT NULL,
    file_hash BINARY(32) NULL,
    generation_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_by BIGINT NOT NULL,
    signed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_contract_version(order_id,version),
    UNIQUE KEY uk_contract_current(order_id,active_slot),
    UNIQUE KEY uk_contract_generation(generation_key),
    UNIQUE KEY uk_contract_file(file_storage_key),
    CONSTRAINT fk_contract_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_contract_creator FOREIGN KEY (created_by) REFERENCES members(id),
    CONSTRAINT ck_contract_state CHECK (status IN ('DRAFT','GENERATING','READY','SIGNED','FAILED','SUPERSEDED')),
    CONSTRAINT ck_contract_current CHECK ((status='SUPERSEDED' AND active_slot IS NULL) OR (status<>'SUPERSEDED' AND active_slot IS NOT NULL AND active_slot=1)),
    CONSTRAINT ck_contract_version CHECK (version>=1),
    CONSTRAINT ck_contract_file CHECK (status NOT IN ('READY','SIGNED') OR (file_storage_key IS NOT NULL AND file_backend IS NOT NULL AND file_size IS NOT NULL AND file_size>0 AND file_hash IS NOT NULL)),
    CONSTRAINT ck_contract_signed CHECK ((status='SIGNED' AND signed_at IS NOT NULL) OR (status<>'SIGNED' AND signed_at IS NULL)),
    CONSTRAINT ck_contract_times CHECK (updated_at>=created_at AND (signed_at IS NULL OR (signed_at>=created_at AND updated_at>=signed_at)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE contract_signatures (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    party_role VARCHAR(10) NOT NULL,
    signed_content_hash BINARY(32) NOT NULL,
    signed_file_hash BINARY(32) NOT NULL,
    signed_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_signature_role(contract_id,party_role),
    UNIQUE KEY uk_signature_member(contract_id,member_id),
    CONSTRAINT fk_signature_contract FOREIGN KEY (contract_id) REFERENCES order_contracts(id),
    CONSTRAINT fk_signature_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT ck_signature_role CHECK (party_role IN ('BUYER','SELLER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE order_response_timers (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    delivery_id BIGINT NOT NULL,
    timer_type VARCHAR(24) NOT NULL,
    basis_at DATETIME(6) NOT NULL,
    eligible_at DATETIME(6) NOT NULL,
    started_by BIGINT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    status VARCHAR(16) NOT NULL,
    active_slot INT NULL,
    ended_at DATETIME(6) NULL,
    ended_by BIGINT NULL,
    end_reason VARCHAR(24) NULL,
    admin_stop_reason VARCHAR(1000) NULL,
    UNIQUE KEY uk_timer_cycle(order_id,timer_type,delivery_id),
    UNIQUE KEY uk_timer_active(order_id,active_slot),
    UNIQUE KEY uk_timer_id_order(id,order_id),
    KEY ix_timer_due(status,expires_at,id),
    CONSTRAINT fk_timer_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_timer_delivery FOREIGN KEY (delivery_id,order_id) REFERENCES deliveries(id,order_id),
    CONSTRAINT fk_timer_starter FOREIGN KEY (started_by) REFERENCES members(id),
    CONSTRAINT fk_timer_ender FOREIGN KEY (ended_by) REFERENCES members(id),
    CONSTRAINT ck_timer_type CHECK (timer_type IN ('REVIEW_RESPONSE','BALANCE_PAYMENT')),
    CONSTRAINT ck_timer_window CHECK (eligible_at=TIMESTAMPADD(HOUR,48,basis_at) AND started_at>=eligible_at AND expires_at=TIMESTAMPADD(HOUR,168,started_at)),
    CONSTRAINT ck_timer_state CHECK ((status='ACTIVE' AND active_slot IS NOT NULL AND active_slot=1 AND ended_at IS NULL AND ended_by IS NULL AND end_reason IS NULL AND admin_stop_reason IS NULL) OR (status='EXPIRED' AND active_slot IS NULL AND ended_at IS NOT NULL AND ended_at>=expires_at AND ended_by IS NULL AND end_reason IS NOT NULL AND end_reason='TIMEOUT' AND admin_stop_reason IS NULL) OR (status='CANCELLED' AND active_slot IS NULL AND ended_at IS NOT NULL AND ended_by IS NOT NULL AND end_reason IS NOT NULL AND end_reason IN ('BUYER_RESPONSE','PAYMENT','ADMIN_STOP','ORDER_CLOSED'))),
    CONSTRAINT ck_timer_end_time CHECK (ended_at IS NULL OR ended_at>=started_at),
    CONSTRAINT ck_timer_admin CHECK ((end_reason='ADMIN_STOP' AND admin_stop_reason IS NOT NULL AND CHAR_LENGTH(TRIM(admin_stop_reason)) BETWEEN 1 AND 1000) OR ((end_reason IS NULL OR end_reason<>'ADMIN_STOP') AND admin_stop_reason IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE order_chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    sequence BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    client_message_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_chat_sequence(order_id,sequence),
    UNIQUE KEY uk_chat_request(order_id,sender_id,client_message_id),
    CONSTRAINT fk_chat_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_chat_sender FOREIGN KEY (sender_id) REFERENCES members(id),
    CONSTRAINT ck_chat_sequence CHECK (sequence>0),
    CONSTRAINT ck_chat_content CHECK (CHAR_LENGTH(content) BETWEEN 1 AND 2000 AND CHAR_LENGTH(TRIM(content))>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE outbox_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    type VARCHAR(60) NOT NULL,
    schema_version INT NOT NULL,
    aggregate_type VARCHAR(30) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    lease_owner CHAR(36) NULL,
    lease_until DATETIME(6) NULL,
    last_error_code VARCHAR(100) NULL,
    processed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_outbox_event(event_id),
    KEY ix_outbox_due(status,next_attempt_at,id),
    CONSTRAINT ck_outbox_state CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED')),
    CONSTRAINT ck_outbox_numbers CHECK (attempt_count>=0 AND schema_version>=1),
    CONSTRAINT ck_outbox_lease CHECK ((status='PROCESSING' AND lease_owner IS NOT NULL AND lease_until IS NOT NULL) OR (status<>'PROCESSING' AND lease_owner IS NULL AND lease_until IS NULL)),
    CONSTRAINT ck_outbox_complete CHECK ((status='COMPLETED' AND processed_at IS NOT NULL) OR (status<>'COMPLETED' AND processed_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    type VARCHAR(60) NOT NULL,
    order_id BIGINT NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_notification_event(recipient_id,event_id,type),
    KEY ix_notification_list(recipient_id,created_at DESC,id DESC),
    KEY ix_notification_unread(recipient_id,read_at,id),
    CONSTRAINT fk_notification_member FOREIGN KEY (recipient_id) REFERENCES members(id),
    CONSTRAINT fk_notification_event FOREIGN KEY (event_id) REFERENCES outbox_events(event_id),
    CONSTRAINT fk_notification_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT ck_notification_read CHECK (read_at IS NULL OR read_at>=created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE account_challenges (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    purpose VARCHAR(24) NOT NULL,
    member_id BIGINT NULL,
    email VARCHAR(254) NOT NULL,
    token_hash BINARY(32) NOT NULL,
    credential_version BIGINT NULL,
    issued_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    verified_at DATETIME(6) NULL,
    consumed_at DATETIME(6) NULL,
    delivery_status VARCHAR(16) NOT NULL,
    last_error_code VARCHAR(100) NULL,
    UNIQUE KEY uk_challenge_hash(token_hash),
    KEY ix_challenge_email(purpose,email,issued_at),
    KEY ix_challenge_expiry(expires_at,id),
    CONSTRAINT fk_challenge_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT ck_challenge_purpose CHECK ((purpose='SIGNUP_EMAIL' AND member_id IS NULL AND credential_version IS NULL) OR (purpose IN ('EXISTING_EMAIL','PASSWORD_RESET') AND member_id IS NOT NULL AND credential_version IS NOT NULL AND credential_version>=0)),
    CONSTRAINT ck_challenge_time CHECK (expires_at>issued_at AND (verified_at IS NULL OR (verified_at>=issued_at AND verified_at<expires_at)) AND (consumed_at IS NULL OR (consumed_at>=issued_at AND consumed_at<expires_at))),
    CONSTRAINT ck_challenge_delivery CHECK (delivery_status IN ('PENDING','SENT','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE auth_request_limits (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    scope VARCHAR(30) NOT NULL,
    subject_hmac BINARY(32) NOT NULL,
    window_start DATETIME(6) NOT NULL,
    request_count INT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_auth_limit(scope,subject_hmac,window_start),
    KEY ix_auth_limit_expiry(expires_at,id),
    CONSTRAINT ck_auth_limit_count CHECK (request_count>=0),
    CONSTRAINT ck_auth_limit_time CHECK (expires_at>window_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE support_inquiries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    requester_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    cancellation_request_id BIGINT NULL,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    subject VARCHAR(150) NOT NULL,
    assigned_admin_id BIGINT NULL,
    resolved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_inquiry_id_order(id,order_id),
    KEY ix_inquiry_requester(requester_id,created_at,id),
    KEY ix_inquiry_queue(status,created_at,id),
    CONSTRAINT fk_inquiry_member FOREIGN KEY (requester_id) REFERENCES members(id),
    CONSTRAINT fk_inquiry_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_inquiry_cancel FOREIGN KEY (cancellation_request_id,order_id) REFERENCES cancellation_requests(id,order_id),
    CONSTRAINT fk_inquiry_admin FOREIGN KEY (assigned_admin_id) REFERENCES members(id),
    CONSTRAINT ck_inquiry_type CHECK ((type='CANCELLATION_REVIEW' AND order_id IS NOT NULL AND cancellation_request_id IS NOT NULL) OR (type='GENERAL' AND cancellation_request_id IS NULL)),
    CONSTRAINT ck_inquiry_status CHECK ((status IN ('OPEN','IN_REVIEW') AND resolved_at IS NULL) OR (status='CLOSED' AND resolved_at IS NOT NULL)),
    CONSTRAINT ck_inquiry_time CHECK (updated_at>=created_at AND (resolved_at IS NULL OR (resolved_at>=created_at AND updated_at>=resolved_at)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE support_messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    inquiry_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    client_message_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_support_message(inquiry_id,sender_id,client_message_id),
    KEY ix_support_message_list(inquiry_id,id),
    CONSTRAINT fk_support_message_inquiry FOREIGN KEY (inquiry_id) REFERENCES support_inquiries(id),
    CONSTRAINT fk_support_message_sender FOREIGN KEY (sender_id) REFERENCES members(id),
    CONSTRAINT ck_support_content CHECK (CHAR_LENGTH(content) BETWEEN 1 AND 5000 AND CHAR_LENGTH(TRIM(content))>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE order_closures (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    reason VARCHAR(40) NOT NULL,
    source_timer_id BIGINT NULL,
    source_cancellation_request_id BIGINT NULL,
    inquiry_id BIGINT NULL,
    actor_type VARCHAR(10) NOT NULL,
    actor_id BIGINT NULL,
    paid_amount BIGINT NOT NULL,
    buyer_refund_amount BIGINT NOT NULL,
    seller_gross_amount BIGINT NOT NULL,
    fee_amount BIGINT NOT NULL,
    seller_net_amount BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_closure_order(order_id),
    CONSTRAINT fk_closure_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_closure_timer FOREIGN KEY (source_timer_id,order_id) REFERENCES order_response_timers(id,order_id),
    CONSTRAINT fk_closure_cancellation FOREIGN KEY (source_cancellation_request_id,order_id) REFERENCES cancellation_requests(id,order_id),
    CONSTRAINT fk_closure_inquiry FOREIGN KEY (inquiry_id,order_id) REFERENCES support_inquiries(id,order_id),
    CONSTRAINT fk_closure_actor FOREIGN KEY (actor_id) REFERENCES members(id),
    CONSTRAINT ck_closure_sums CHECK (paid_amount BETWEEN 1 AND 1000000 AND buyer_refund_amount>=0 AND seller_gross_amount>=0 AND fee_amount>=0 AND seller_net_amount>=0 AND paid_amount=buyer_refund_amount+seller_gross_amount AND seller_gross_amount=fee_amount+seller_net_amount),
    CONSTRAINT ck_closure_kind CHECK ((reason='COMPLETED' AND actor_type='MEMBER' AND actor_id IS NOT NULL AND source_timer_id IS NULL AND source_cancellation_request_id IS NULL AND inquiry_id IS NULL AND buyer_refund_amount=0 AND fee_amount=FLOOR(seller_gross_amount*1000/10000)) OR (reason IN ('MUTUAL_CANCELLATION','ADMIN_CANCELLATION') AND actor_type='MEMBER' AND actor_id IS NOT NULL AND source_timer_id IS NULL AND source_cancellation_request_id IS NOT NULL AND buyer_refund_amount=paid_amount AND seller_gross_amount=0 AND (reason<>'ADMIN_CANCELLATION' OR inquiry_id IS NOT NULL)) OR (reason IN ('REVIEW_NO_RESPONSE','BALANCE_UNPAID') AND actor_type='SYSTEM' AND actor_id IS NULL AND source_timer_id IS NOT NULL AND source_cancellation_request_id IS NULL AND inquiry_id IS NULL AND seller_gross_amount=FLOOR(paid_amount*3000/10000) AND fee_amount=FLOOR(seller_gross_amount*100/10000)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
