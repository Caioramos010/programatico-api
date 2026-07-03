ALTER TABLE user_settings
    ADD COLUMN totp_enabled BIT(1) NOT NULL DEFAULT 0,
    ADD COLUMN totp_secret VARCHAR(128) NULL,
    ADD COLUMN totp_pending_secret VARCHAR(128) NULL;
