ALTER TABLE user_settings
    ADD COLUMN IF NOT EXISTS two_factor_enabled BIT(1) NOT NULL DEFAULT 1;
