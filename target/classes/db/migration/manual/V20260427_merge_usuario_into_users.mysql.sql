-- Migration manual (MySQL) para cenário em que `users` já foi criado
-- e ainda existe tabela antiga `usuario`.
-- Copia dados faltantes de `usuario` para `users` e remove a tabela antiga.

SET @schema_name := DATABASE();

SET @has_users := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = @schema_name
      AND table_name = 'users'
);

SET @has_usuario := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = @schema_name
      AND table_name = 'usuario'
);

SET @copy_data := IF(
    @has_users > 0 AND @has_usuario > 0,
    'INSERT INTO users (
        id, username, email, password, age, active,
        activation_code, password_reset_code, password_reset_code_expires_at,
        account_deletion_code, account_deletion_code_expires_at,
        created_at, updated_at, icon, skill_level, subscription_type, role
    )
    SELECT
        u.id, u.username, u.email, u.senha, u.idade, u.ativo,
        u.codigo_ativacao, u.codigo_redefinicao_senha, u.data_expiracao_codigo_redefinicao,
        u.codigo_exclusao_conta, u.data_expiracao_codigo_exclusao,
        u.data_criacao, u.data_atualizacao, u.icon, u.nivel_habilidade, u.subscription_type, u.role
    FROM usuario u
    WHERE NOT EXISTS (
        SELECT 1 FROM users x WHERE x.id = u.id
    )',
    'SELECT "skip: copy usuario -> users"'
);
PREPARE stmt FROM @copy_data;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_old_table := IF(
    @has_users > 0 AND @has_usuario > 0,
    'DROP TABLE usuario',
    'SELECT "skip: drop usuario"'
);
PREPARE stmt FROM @drop_old_table;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
