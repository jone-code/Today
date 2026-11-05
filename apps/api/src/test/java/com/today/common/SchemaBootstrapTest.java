package com.today.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SchemaBootstrapTest {

  @Test
  void missingCriticalTableBlocksStartupEvenWhenAutoMigrationDisabled() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    Statement statement = mock(Statement.class);
    ResultSet result = mock(ResultSet.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenReturn(result);

    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> new SchemaBootstrap(dataSource, false).ensureSchema());

    assertTrue(error.getMessage().contains("memory_sources"));
    ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
    verify(statement, org.mockito.Mockito.atLeastOnce()).executeQuery(query.capture());
    assertTrue(query.getAllValues().stream().anyMatch(sql -> sql.contains("memory_sources")));
  }

  @Test
  void schemaVerificationFailureBlocksStartup() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new SQLException("database unavailable"));

    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> new SchemaBootstrap(dataSource, false).ensureSchema());

    assertTrue(error.getMessage().contains("verification-failed"));
  }

  @Test
  void verifiedSchemaStartsWithAutoMigrationDisabled() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    Statement statement = mock(Statement.class);
    ResultSet result = mock(ResultSet.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenReturn(result);
    when(result.next()).thenReturn(true);

    assertDoesNotThrow(() -> new SchemaBootstrap(dataSource, false).ensureSchema());
  }

  @Test
  void missingRetryColumnOrUniqueIndexBlocksStartup() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    Statement statement = mock(Statement.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      ResultSet result = mock(ResultSet.class);
      when(result.next()).thenReturn(!sql.contains("column_name = 'generation_attempt'")
          && !sql.contains("index_name = 'uk_conversation_client_message'"));
      return result;
    });

    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> new SchemaBootstrap(dataSource, false).ensureSchema());

    assertTrue(error.getMessage().contains("conversation_messages:generation_attempt"));
    assertTrue(error.getMessage().contains("conversation_messages:uk_conversation_client_message"));
  }

  @Test
  void missingAvatarCleanupMigrationBlocksStartup() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    Statement statement = mock(Statement.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      ResultSet result = mock(ResultSet.class);
      when(result.next()).thenReturn(!sql.contains("column_name = 'next_cleanup_at'")
          && !sql.contains("index_name = 'idx_agent_media_cleanup_due'"));
      return result;
    });

    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> new SchemaBootstrap(dataSource, false).ensureSchema());

    assertTrue(error.getMessage().contains("agent_media:next_cleanup_at"));
    assertTrue(error.getMessage().contains("agent_media:idx_agent_media_cleanup_due"));
  }
}
