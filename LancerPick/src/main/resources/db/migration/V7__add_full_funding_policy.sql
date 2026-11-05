-- V3 full funding is opt-in. Preserve LEGACY_V1 and CONTRACT_V2 data and signed contracts.
ALTER TABLE orders DROP CHECK ck_orders_policy,
 ADD CONSTRAINT ck_orders_policy CHECK (policy_version IN ('LEGACY_V1','CONTRACT_V2','CONTRACT_V3')),
 ADD CONSTRAINT ck_orders_v3_price CHECK (policy_version<>'CONTRACT_V3' OR price_amount>=10000);
ALTER TABLE payments DROP CHECK ck_payments_installment_policy,
 ADD CONSTRAINT ck_payments_installment_policy CHECK (
 (order_policy_version='LEGACY_V1' AND installment_type='FULL') OR
 (order_policy_version='CONTRACT_V2' AND installment_type IN ('DEPOSIT','BALANCE')) OR
 (order_policy_version='CONTRACT_V3' AND installment_type='FULL')),
 ADD UNIQUE KEY uk_payment_id_order(id,order_id);
-- Keep the one-payment-per-order UNIQUE and full-refund CHECK: no new installment activation.
ALTER TABLE order_trade_terms ADD funding_mode VARCHAR(20) NOT NULL DEFAULT 'INSTALLMENT',
 DROP CHECK ck_trade_terms_v2,
 ADD CONSTRAINT ck_trade_terms_mode CHECK (
 (funding_mode='INSTALLMENT' AND deposit_rate_bps=5000 AND compensation_rate_bps=3000 AND normal_fee_rate_bps=1000 AND compensation_fee_rate_bps=100 AND review_wait_hours=48 AND response_window_hours=168)
 OR (funding_mode='FULL_HOLD' AND deposit_rate_bps=10000 AND compensation_rate_bps=0 AND normal_fee_rate_bps=1000 AND compensation_fee_rate_bps=0 AND review_wait_hours=48 AND response_window_hours=168));

CREATE TABLE order_funds (
 order_id BIGINT NOT NULL PRIMARY KEY,
 payment_id BIGINT NOT NULL,
 amount BIGINT NOT NULL,
 remaining_amount BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL,
 held_at DATETIME(6) NOT NULL,
 resolved_at DATETIME(6) NULL,
 UNIQUE KEY uk_order_funds_payment(payment_id),
 CONSTRAINT fk_order_funds_order FOREIGN KEY(order_id) REFERENCES orders(id),
 CONSTRAINT fk_order_funds_payment FOREIGN KEY(payment_id,order_id) REFERENCES payments(id,order_id),
 CONSTRAINT ck_order_funds_amount CHECK (amount BETWEEN 10000 AND 1000000),
 CONSTRAINT ck_order_funds_state CHECK (
 (status='HELD' AND remaining_amount=amount AND resolved_at IS NULL)
 OR (status IN ('RELEASED','REFUNDED') AND remaining_amount=0 AND resolved_at IS NOT NULL AND resolved_at>=held_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

ALTER TABLE wallet_entries DROP CHECK ck_wallet_entries_type, DROP CHECK ck_wallet_entries_amount,
 ADD CONSTRAINT ck_wallet_entries_type CHECK (type IN ('INITIAL_GRANT','PAYMENT','REFUND','TOPUP','SELLER_PAYOUT')),
 ADD CONSTRAINT ck_wallet_entries_amount CHECK (
 (type='INITIAL_GRANT' AND payment_id IS NULL AND topup_id IS NULL AND amount_delta=1000000)
 OR (type='PAYMENT' AND payment_id IS NOT NULL AND topup_id IS NULL AND amount_delta BETWEEN -1000000 AND -1)
 OR (type IN ('REFUND','SELLER_PAYOUT') AND payment_id IS NOT NULL AND topup_id IS NULL AND amount_delta BETWEEN 1 AND 1000000)
 OR (type='TOPUP' AND payment_id IS NULL AND topup_id IS NOT NULL AND amount_delta BETWEEN 1 AND 1000000));

ALTER TABLE order_response_timers ADD cycle_no INT NOT NULL DEFAULT 1,
 DROP INDEX uk_timer_cycle,
 ADD UNIQUE KEY uk_timer_cycle(order_id,timer_type,delivery_id,cycle_no),
 ADD CONSTRAINT ck_timer_cycle CHECK (cycle_no>=1),
 DROP CHECK ck_timer_type,
 ADD CONSTRAINT ck_timer_type CHECK (timer_type IN ('REVIEW_RESPONSE','BALANCE_PAYMENT','AUTO_CONFIRM'));

-- Reuse existing closure shape for normal SYSTEM completion; preserve old compensation rules.
ALTER TABLE order_closures DROP CHECK ck_closure_kind,
 ADD CONSTRAINT ck_closure_kind CHECK (
 (reason='COMPLETED' AND actor_type='MEMBER' AND actor_id IS NOT NULL AND source_timer_id IS NULL AND source_cancellation_request_id IS NULL AND inquiry_id IS NULL AND buyer_refund_amount=0 AND fee_amount=FLOOR(seller_gross_amount*1000/10000))
 OR (reason='AUTO_CONFIRMED' AND actor_type='SYSTEM' AND actor_id IS NULL AND source_timer_id IS NOT NULL AND source_cancellation_request_id IS NULL AND inquiry_id IS NULL AND buyer_refund_amount=0 AND fee_amount=FLOOR(seller_gross_amount*1000/10000))
 OR (reason IN ('MUTUAL_CANCELLATION','ADMIN_CANCELLATION') AND actor_type='MEMBER' AND actor_id IS NOT NULL AND source_timer_id IS NULL AND source_cancellation_request_id IS NOT NULL AND buyer_refund_amount=paid_amount AND seller_gross_amount=0 AND (reason<>'ADMIN_CANCELLATION' OR inquiry_id IS NOT NULL))
 OR (reason IN ('REVIEW_NO_RESPONSE','BALANCE_UNPAID') AND actor_type='SYSTEM' AND actor_id IS NULL AND source_timer_id IS NOT NULL AND source_cancellation_request_id IS NULL AND inquiry_id IS NULL AND seller_gross_amount=FLOOR(paid_amount*3000/10000) AND fee_amount=FLOOR(seller_gross_amount*100/10000)));
