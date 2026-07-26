package com.today.common;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/**
 * Ensure required tables exist on boot (Compose volumes created before schema grew).
 *
 * <p>Runs during bean init, before {@code @Scheduled} tasks start.
 */
@Component
public class SchemaBootstrap {

  private static final Logger log = LoggerFactory.getLogger(SchemaBootstrap.class);

  private static final String[] SCRIPTS = {
    "db/schema.sql",
    "db/migration-auth-reminder.sql",
    "db/migration-memory-embedding.sql",
    "db/migration-todo-punch.sql",
    "db/migration-memory-manage.sql",
    "db/migration-proactive-events.sql",
    "db/migration-checkin-ai-jobs.sql",
    "db/migration-ai-call-logs.sql"
  };

  private static final String[] REQUIRED = {
    "users",
    "checkins",
    "day_summaries",
    "memories",
    "reminders",
    "reminder_deliveries",
    "todos",
    "punch_habits",
    "punch_logs",
    "proactive_prompt_events",
    "checkin_ai_jobs",
    "ai_call_logs"
  };

  private final DataSource dataSource;
  private final boolean enabled;

  public SchemaBootstrap(
      DataSource dataSource,
      @Value("${today.schema.bootstrap:true}") boolean enabled) {
    this.dataSource = dataSource;
    this.enabled = enabled;
  }

  @PostConstruct
  public void ensureSchema() {
    if (!enabled) {
      log.info("schema bootstrap disabled");
      return;
    }
    log.info("schema bootstrap: applying classpath SQL (continueOnError for CREATE DATABASE)");
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
    populator.setContinueOnError(true);
    populator.setIgnoreFailedDrops(true);
    populator.setSeparator(";");
    for (String path : SCRIPTS) {
      ClassPathResource resource = new ClassPathResource(path);
      if (!resource.exists()) {
        log.warn("schema script missing on classpath: {}", path);
        continue;
      }
      populator.addScript(resource);
    }
    DatabasePopulatorUtils.execute(populator, dataSource);

    List<String> missing = missingTables();
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "Database schema incomplete, missing tables: "
              + missing
              + ". Run: npm run db:init:docker  (or grant DDL to MYSQL_USER)");
    }
    log.info("schema bootstrap: required tables present");
  }

  private List<String> missingTables() {
    List<String> missing = new ArrayList<>();
    try (Connection c = dataSource.getConnection();
        Statement st = c.createStatement()) {
      for (String table : REQUIRED) {
        try (ResultSet rs =
            st.executeQuery(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = '"
                    + table
                    + "' LIMIT 1")) {
          if (!rs.next()) {
            missing.add(table);
          }
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException("schema bootstrap verification failed: " + e.getMessage(), e);
    }
    return missing;
  }
}
