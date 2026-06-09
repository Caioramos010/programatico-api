-- Migration manual (MySQL) para padronizar nome da tabela de usuário em inglês.
-- Renomeia `ususario` ou `usuarios` para `users`, se existirem.

SET @schema_name := DATABASE();

SET @has_users := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = @schema_name
      AND table_name = 'users'
);

SET @has_ususario := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = @schema_name
      AND table_name = 'ususario'
);

SET @has_usuarios := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = @schema_name
      AND table_name = 'usuarios'
);

SET @has_usuario := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = @schema_name
      AND table_name = 'usuario'
);

SET @rename_from_ususario := IF(
    @has_users = 0 AND @has_ususario > 0,
    'RENAME TABLE ususario TO users',
    'SELECT "skip: ususario -> users"'
);
PREPARE stmt FROM @rename_from_ususario;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @rename_from_usuarios := IF(
    @has_users = 0 AND @has_usuarios > 0,
    'RENAME TABLE usuarios TO users',
    'SELECT "skip: usuarios -> users"'
);
PREPARE stmt FROM @rename_from_usuarios;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @rename_from_usuario := IF(
    @has_users = 0 AND @has_usuario > 0,
    'RENAME TABLE usuario TO users',
    'SELECT "skip: usuario -> users"'
);
PREPARE stmt FROM @rename_from_usuario;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
