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
