ALTER TABLE users
    ADD COLUMN login_code_failed_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN login_code_blocked_until DATETIME(6) NULL,
    ADD COLUMN activation_code_failed_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN activation_code_blocked_until DATETIME(6) NULL,
    ADD COLUMN password_reset_code_failed_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN password_reset_code_blocked_until DATETIME(6) NULL;
