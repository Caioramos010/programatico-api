package com.programatico.api.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Component
@Order(10)
@RequiredArgsConstructor
public class SchemaMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!isMySql()) {
            return;
        }

        String legacyTable = detectLegacyTable();
        boolean usersExists = tableExists("users");

        if (!usersExists && legacyTable != null) {
            execute("RENAME TABLE " + legacyTable + " TO users");
            log.info("Tabela '{}' renomeada para 'users'.", legacyTable);
            usersExists = true;
            legacyTable = null;
        }

        if (!usersExists) {
            return;
        }

        ensureUsersColumnsInEnglish();
        backfillRootSubscriptionExpiresAt();
        ensureSubscriptionAutoRenewColumn();
        ensureVerificationCodeAttemptColumns();
        ensureUserSettingsColumns();
        ensureTwoFactorBackupCodesTable();

        if (legacyTable != null) {
            mergeLegacyData(legacyTable);
            repointForeignKeys(legacyTable, "users");
            execute("DROP TABLE " + legacyTable);
            log.info("Tabela legada '{}' removida após migração para 'users'.", legacyTable);
        }
    }

    private boolean isMySql() {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            return productName != null && productName.toLowerCase().contains("mysql");
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível identificar o banco de dados.", e);
        }
    }

    private String detectLegacyTable() {
        if (tableExists("usuarios")) {
            return "usuarios";
        }
        if (tableExists("usuario")) {
            return "usuario";
        }
        if (tableExists("ususario")) {
            return "ususario";
        }
        return null;
    }

    private boolean tableExists(String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(connection.getCatalog(), null, tableName, new String[]{"TABLE"})) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao verificar existência da tabela: " + tableName, e);
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        String sql = """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    private void ensureUsersColumnsInEnglish() {
        Map<String, String> renames = Map.ofEntries(
                Map.entry("senha", "password"),
                Map.entry("idade", "age"),
                Map.entry("ativo", "active"),
                Map.entry("codigo_ativacao", "activation_code"),
                Map.entry("codigo_redefinicao_senha", "password_reset_code"),
                Map.entry("data_expiracao_codigo_redefinicao", "password_reset_code_expires_at"),
                Map.entry("codigo_exclusao_conta", "account_deletion_code"),
                Map.entry("data_expiracao_codigo_exclusao", "account_deletion_code_expires_at"),
                Map.entry("data_criacao", "created_at"),
                Map.entry("data_atualizacao", "updated_at"),
                Map.entry("nivel_habilidade", "skill_level")
        );

        for (Map.Entry<String, String> entry : renames.entrySet()) {
            String oldColumn = entry.getKey();
            String newColumn = entry.getValue();

            if (columnExists("users", oldColumn) && !columnExists("users", newColumn)) {
                execute("ALTER TABLE users RENAME COLUMN " + oldColumn + " TO " + newColumn);
                log.info("Coluna 'users.{}' renomeada para 'users.{}'.", oldColumn, newColumn);
            }
        }
    }

    private void backfillRootSubscriptionExpiresAt() {
        if (!columnExists("users", "subscription_expires_at")) {
            return;
        }
        int updated = jdbcTemplate.update("""
                UPDATE users
                SET subscription_expires_at = DATE_ADD(NOW(), INTERVAL 30 DAY)
                WHERE subscription_type = 'ROOT'
                  AND subscription_expires_at IS NULL
                """);
        if (updated > 0) {
            log.info("Preenchido subscription_expires_at para {} usuário(s) ROOT existente(s).", updated);
        }
    }

    private void ensureSubscriptionAutoRenewColumn() {
        addColumnIfMissing("users", "subscription_auto_renew",
                "ALTER TABLE users ADD COLUMN subscription_auto_renew BIT(1) NOT NULL DEFAULT 1");
    }

    private void ensureVerificationCodeAttemptColumns() {
        addColumnIfMissing("users", "login_code_failed_attempts",
                "ALTER TABLE users ADD COLUMN login_code_failed_attempts INT NOT NULL DEFAULT 0");
        addColumnIfMissing("users", "login_code_blocked_until",
                "ALTER TABLE users ADD COLUMN login_code_blocked_until DATETIME(6) NULL");
        addColumnIfMissing("users", "activation_code_failed_attempts",
                "ALTER TABLE users ADD COLUMN activation_code_failed_attempts INT NOT NULL DEFAULT 0");
        addColumnIfMissing("users", "activation_code_blocked_until",
                "ALTER TABLE users ADD COLUMN activation_code_blocked_until DATETIME(6) NULL");
        addColumnIfMissing("users", "password_reset_code_failed_attempts",
                "ALTER TABLE users ADD COLUMN password_reset_code_failed_attempts INT NOT NULL DEFAULT 0");
        addColumnIfMissing("users", "password_reset_code_blocked_until",
                "ALTER TABLE users ADD COLUMN password_reset_code_blocked_until DATETIME(6) NULL");
    }

    private void ensureUserSettingsColumns() {
        if (!tableExists("user_settings")) {
            return;
        }
        addColumnIfMissing("user_settings", "disable_update_notifications",
                "ALTER TABLE user_settings ADD COLUMN disable_update_notifications BIT(1) NOT NULL DEFAULT 0");
        addColumnIfMissing("user_settings", "disable_daystreak_notifications",
                "ALTER TABLE user_settings ADD COLUMN disable_daystreak_notifications BIT(1) NOT NULL DEFAULT 0");
        addColumnIfMissing("user_settings", "disable_mission_notifications",
                "ALTER TABLE user_settings ADD COLUMN disable_mission_notifications BIT(1) NOT NULL DEFAULT 0");
        addColumnIfMissing("user_settings", "disable_subscription_notifications",
                "ALTER TABLE user_settings ADD COLUMN disable_subscription_notifications BIT(1) NOT NULL DEFAULT 0");
        addColumnIfMissing("user_settings", "disable_email_notifications",
                "ALTER TABLE user_settings ADD COLUMN disable_email_notifications BIT(1) NOT NULL DEFAULT 0");
        addColumnIfMissing("user_settings", "disable_all_notifications",
                "ALTER TABLE user_settings ADD COLUMN disable_all_notifications BIT(1) NOT NULL DEFAULT 0");
        addColumnIfMissing("user_settings", "two_factor_enabled",
                "ALTER TABLE user_settings ADD COLUMN two_factor_enabled BIT(1) NOT NULL DEFAULT 1");
        addColumnIfMissing("user_settings", "totp_enabled",
                "ALTER TABLE user_settings ADD COLUMN totp_enabled BIT(1) NOT NULL DEFAULT 0");
        addColumnIfMissing("user_settings", "totp_secret",
                "ALTER TABLE user_settings ADD COLUMN totp_secret VARCHAR(128) NULL");
        addColumnIfMissing("user_settings", "totp_pending_secret",
                "ALTER TABLE user_settings ADD COLUMN totp_pending_secret VARCHAR(128) NULL");
    }

    private void ensureTwoFactorBackupCodesTable() {
        if (tableExists("two_factor_backup_codes")) {
            return;
        }
        execute("""
                CREATE TABLE two_factor_backup_codes (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    code_hash VARCHAR(255) NOT NULL,
                    used_at DATETIME(6) NULL,
                    created_at DATETIME(6) NOT NULL,
                    CONSTRAINT fk_backup_codes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    INDEX idx_backup_codes_user_id (user_id)
                )
                """);
        log.info("Tabela 'two_factor_backup_codes' criada.");
    }

    private void addColumnIfMissing(String table, String column, String ddl) {
        if (!columnExists(table, column)) {
            execute(ddl);
            log.info("Coluna '{}.{}' adicionada.", table, column);
        }
    }

    private void mergeLegacyData(String legacyTable) {
        if (!tableExists(legacyTable)) {
            return;
        }

        String insertSql = """
                INSERT INTO users (
                    id, username, email, password, age, active,
                    activation_code, password_reset_code, password_reset_code_expires_at,
                    account_deletion_code, account_deletion_code_expires_at,
                    created_at, updated_at, icon, skill_level, subscription_type, role
                )
                SELECT
                    u.id, u.username, u.email,
                    %s, %s, %s,
                    %s, %s, %s,
                    %s, %s,
                    %s, %s, u.icon, %s, u.subscription_type, u.role
                FROM %s u
                WHERE NOT EXISTS (
                    SELECT 1 FROM users x WHERE x.id = u.id
                )
                """.formatted(
                columnExpr(legacyTable, "password", "senha"),
                columnExpr(legacyTable, "age", "idade"),
                columnExpr(legacyTable, "active", "ativo"),
                columnExpr(legacyTable, "activation_code", "codigo_ativacao"),
                columnExpr(legacyTable, "password_reset_code", "codigo_redefinicao_senha"),
                columnExpr(legacyTable, "password_reset_code_expires_at", "data_expiracao_codigo_redefinicao"),
                columnExpr(legacyTable, "account_deletion_code", "codigo_exclusao_conta"),
                columnExpr(legacyTable, "account_deletion_code_expires_at", "data_expiracao_codigo_exclusao"),
                columnExpr(legacyTable, "created_at", "data_criacao"),
                columnExpr(legacyTable, "updated_at", "data_atualizacao"),
                columnExpr(legacyTable, "skill_level", "nivel_habilidade"),
                legacyTable
        );

        execute(insertSql);
        log.info("Dados da tabela '{}' mesclados em 'users'.", legacyTable);
    }

    private String columnExpr(String tableName, String englishColumn, String portugueseColumn) {
        if (columnExists(tableName, englishColumn)) {
            return "u." + englishColumn;
        }
        return "u." + portugueseColumn;
    }

    private void repointForeignKeys(String fromTable, String toTable) {
        String sql = """
                SELECT
                    kcu.TABLE_NAME AS table_name,
                    kcu.CONSTRAINT_NAME AS constraint_name,
                    kcu.COLUMN_NAME AS column_name,
                    rc.UPDATE_RULE AS update_rule,
                    rc.DELETE_RULE AS delete_rule
                FROM information_schema.KEY_COLUMN_USAGE kcu
                JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
                  ON rc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
                 AND rc.TABLE_NAME = kcu.TABLE_NAME
                 AND rc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
                WHERE kcu.REFERENCED_TABLE_SCHEMA = DATABASE()
                  AND kcu.REFERENCED_TABLE_NAME = ?
                """;

        List<Map<String, Object>> fks = jdbcTemplate.queryForList(sql, fromTable);
        for (Map<String, Object> fk : fks) {
            String tableName = String.valueOf(fk.get("table_name"));
            String constraintName = String.valueOf(fk.get("constraint_name"));
            String columnName = String.valueOf(fk.get("column_name"));
            String updateRule = String.valueOf(fk.get("update_rule"));
            String deleteRule = String.valueOf(fk.get("delete_rule"));

            execute("ALTER TABLE `" + tableName + "` DROP FOREIGN KEY `" + constraintName + "`");
            execute("ALTER TABLE `" + tableName + "` ADD CONSTRAINT `" + constraintName + "` " +
                    "FOREIGN KEY (`" + columnName + "`) REFERENCES `" + toTable + "`(`id`) " +
                    "ON UPDATE " + updateRule + " ON DELETE " + deleteRule);
            log.info("FK '{}.{}' atualizada para referenciar '{}'.", tableName, constraintName, toTable);
        }
    }

    private void execute(String sql) {
        jdbcTemplate.execute(sql);
    }
}
