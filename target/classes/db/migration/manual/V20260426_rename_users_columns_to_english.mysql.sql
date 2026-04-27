-- Migration manual (MySQL) para renomear colunas de `users` para inglês.
-- Mantém dados existentes; altera apenas nomes de colunas.

SET @schema_name := DATABASE();

SET @has_users := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = @schema_name
      AND table_name = 'users'
);

SET @rename_senha := IF(
    @has_users > 0 AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name AND table_name = 'users' AND column_name = 'senha'
    ),
    'ALTER TABLE users RENAME COLUMN senha TO password',
    'SELECT "skip: senha -> password"'
);
PREPARE stmt FROM @rename_senha;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @rename_idade := IF(
    @has_users > 0 AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name AND table_name = 'users' AND column_name = 'idade'
    ),
    'ALTER TABLE users RENAME COLUMN idade TO age',
    'SELECT "skip: idade -> age"'
);
PREPARE stmt FROM @rename_idade;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @rename_ativo := IF(
    @has_users > 0 AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name AND table_name = 'users' AND column_name = 'ativo'
    ),
    'ALTER TABLE users RENAME COLUMN ativo TO active',
    'SELECT "skip: ativo -> active"'
);
PREPARE stmt FROM @rename_ativo;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @rename_codigo_ativacao := IF(
    @has_users > 0 AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name AND table_name = 'users' AND column_name = 'codigo_ativacao'
    ),
    'ALTER TABLE users RENAME COLUMN codigo_ativacao TO activation_code',
    'SELECT "skip: codigo_ativacao -> activation_code"'
);
PREPARE stmt FROM @rename_codigo_ativacao;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @rename_codigo_redef := IF(
    @has_users > 0 AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name AND table_name = 'users' AND column_name = 'codigo_redefinicao_senha'
    ),
    'ALTER TABLE users RENAME COLUMN codigo_redefinicao_senha TO password_reset_code',
    'SELECT "skip: codigo_redefinicao_senha -> password_reset_code"'
);
PREPARE stmt FROM @rename_codigo_redef;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @rename_data_redef := IF(
    @has_users > 0 AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name AND table_name = 'users' AND column_name = 'data_expiracao_codigo_redefinicao'
    ),
    'ALTER TABLE users RENAME COLUMN data_expiracao_codigo_redefinicao TO password_reset_code_expires_at',
    'SELECT "skip: data_expiracao_codigo_redefinicao -> password_reset_code_expires_at"'
);
PREPARE stmt FROM @rename_data_redef;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @rename_codigo_exclusao := IF(
    @has_users > 0 AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name AND table_name = 'users' AND column_name = 'codigo_exclusao_conta'
    ),
    'ALTER TABLE users RENAME COLUMN codigo_exclusao_conta TO account_deletion_code',
    'SELECT "skip: codigo_exclusao_conta -> account_deletion_code"'
);
PREPARE stmt FROM @rename_codigo_exclusao;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @rename_data_exclusao := IF(
    @has_users > 0 AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name AND table_name = 'users' AND column_name = 'data_expiracao_codigo_exclusao'
    ),
    'ALTER TABLE users RENAME COLUMN data_expiracao_codigo_exclusao TO account_deletion_code_expires_at',
    'SELECT "skip: data_expiracao_codigo_exclusao -> account_deletion_code_expires_at"'
);
PREPARE stmt FROM @rename_data_exclusao;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @rename_data_criacao := IF(
    @has_users > 0 AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name AND table_name = 'users' AND column_name = 'data_criacao'
    ),
    'ALTER TABLE users RENAME COLUMN data_criacao TO created_at',
    'SELECT "skip: data_criacao -> created_at"'
);
PREPARE stmt FROM @rename_data_criacao;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @rename_data_atualizacao := IF(
    @has_users > 0 AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name AND table_name = 'users' AND column_name = 'data_atualizacao'
    ),
    'ALTER TABLE users RENAME COLUMN data_atualizacao TO updated_at',
    'SELECT "skip: data_atualizacao -> updated_at"'
);
PREPARE stmt FROM @rename_data_atualizacao;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @rename_nivel := IF(
    @has_users > 0 AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name AND table_name = 'users' AND column_name = 'nivel_habilidade'
    ),
    'ALTER TABLE users RENAME COLUMN nivel_habilidade TO skill_level',
    'SELECT "skip: nivel_habilidade -> skill_level"'
);
PREPARE stmt FROM @rename_nivel;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
