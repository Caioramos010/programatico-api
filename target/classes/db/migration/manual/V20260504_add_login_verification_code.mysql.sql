-- Código de verificação enviado por e-mail ao fazer login (2ª etapa).
ALTER TABLE users
  ADD COLUMN login_verification_code VARCHAR(20) NULL,
  ADD COLUMN login_verification_code_expires_at DATETIME(6) NULL;
