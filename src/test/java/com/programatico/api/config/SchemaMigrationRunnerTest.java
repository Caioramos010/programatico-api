package com.programatico.api.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchemaMigrationRunnerTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private DataSource dataSource;

    @InjectMocks private SchemaMigrationRunner schemaMigrationRunner;

    @Test
    void runDeveIgnorarQuandoBancoNaoEhMySql() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("H2");

        schemaMigrationRunner.run(new DefaultApplicationArguments());

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void runDeveAplicarMigracoesQuandoMySqlEUsersExiste() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(connection.getCatalog()).thenReturn("programatico");
        when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");

        ResultSet usersExists = mock(ResultSet.class);
        when(usersExists.next()).thenReturn(true);
        ResultSet tableAbsent = mock(ResultSet.class);
        when(tableAbsent.next()).thenReturn(false);

        when(databaseMetaData.getTables(any(), any(), eq("users"), any())).thenReturn(usersExists);
        when(databaseMetaData.getTables(any(), any(), eq("usuarios"), any())).thenReturn(tableAbsent);
        when(databaseMetaData.getTables(any(), any(), eq("usuario"), any())).thenReturn(tableAbsent);
        when(databaseMetaData.getTables(any(), any(), eq("ususario"), any())).thenReturn(tableAbsent);
        when(databaseMetaData.getTables(any(), any(), eq("two_factor_backup_codes"), any())).thenReturn(tableAbsent);
        when(databaseMetaData.getTables(any(), any(), eq("user_settings"), any())).thenReturn(usersExists);

        when(jdbcTemplate.queryForObject(contains("information_schema.columns"), eq(Integer.class), any(), any()))
                .thenReturn(0);

        schemaMigrationRunner.run(new DefaultApplicationArguments());

        verify(jdbcTemplate, atLeastOnce()).execute(contains("CREATE TABLE two_factor_backup_codes"));
    }
}
