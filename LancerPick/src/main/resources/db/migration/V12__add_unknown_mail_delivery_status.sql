-- Delivery bookkeeping only. Never change a token's hash, validity or consumption state.
ALTER TABLE account_challenges
    DROP CHECK ck_challenge_delivery,
    ADD CONSTRAINT ck_challenge_delivery
        CHECK (delivery_status IN ('PENDING','SENT','FAILED','UNKNOWN'));

CREATE INDEX ix_challenge_delivery_pending
    ON account_challenges(delivery_status, issued_at, id);
