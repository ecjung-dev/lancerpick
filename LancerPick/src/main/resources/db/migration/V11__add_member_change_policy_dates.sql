-- Existing password change history is unknown. Start its recommendation clock at rollout,
-- without inventing a historical password change time. New writes set these dates explicitly.
ALTER TABLE members
    ADD COLUMN display_name_changed_at DATETIME(6) NULL,
    ADD COLUMN password_changed_at DATETIME(6) NULL,
    ADD COLUMN password_recommendation_base_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6));
