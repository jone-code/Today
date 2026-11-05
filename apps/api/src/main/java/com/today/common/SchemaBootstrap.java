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

/** Verify the consent and idempotency schema before the API can accept requests. */
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
    "db/migration-ai-call-logs.sql",
    "db/migration-punch-photo.sql",
    "db/migration-agent-conversation.sql",
    "db/migration-conversation-create-idempotency.sql",
    "db/migration-rate-limits.sql",
    "db/migration-create-idempotency.sql",
    "db/migration-punch-photo-cleanup.sql",
    "db/migration-agent-media-cleanup.sql",
    "db/migration-feedback-reports.sql",
    "db/migration-agent-action-undo.sql"
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
    "ai_call_logs",
    "agent_profiles",
    "agent_media",
    "conversations",
    "conversation_messages",
    "agent_proposals",
    "agent_action_logs",
    "memory_sources",
    "vector_sync_jobs",
    "checkin_memory_candidates",
    "followup_topics",
    "one_off_appointments",
    "followup_preferences",
    "inbox_items",
    "api_rate_limits",
    "create_requests",
    "punch_photo_cleanup_jobs",
    "feedback_reports"
  };

  private static final String[] REQUIRED_COLUMNS = {
    "conversations:deleted_at",
    "conversations:client_request_id",
    "conversation_messages:deleted_at",
    "conversation_messages:generation_attempt",
    "conversation_messages:generation_started_at",
    "conversation_messages:client_message_id",
    "agent_proposals:source_message_id",
    "agent_proposals:status",
    "agent_action_logs:undo_snapshot_json",
    "agent_action_logs:undone_at",
    "memory_sources:active",
    "vector_sync_jobs:version",
    "vector_sync_jobs:lease_until",
    "checkin_memory_candidates:source_hash",
    "checkin_memory_candidates:status",
    "followup_topics:source_conversation_id",
    "one_off_appointments:source_conversation_id",
    "inbox_items:source_id",
    "punch_photo_cleanup_jobs:lease_until",
    "agent_media:next_cleanup_at",
    "create_requests:request_hash",
    "create_requests:response_json",
    "feedback_reports:deleted_at"
  };

  private static final String[] REQUIRED_UNIQUE_INDEXES = {
    "conversations:uk_conversation_client_request",
    "conversation_messages:uk_conversation_client_message",
    "conversation_messages:uk_conversation_reply",
    "agent_proposals:uk_agent_proposal_source_kind",
    "checkin_memory_candidates:uk_checkin_candidate_version",
    "one_off_appointments:uk_one_off_proposal",
    "inbox_items:uk_inbox_source_schedule",
    "api_rate_limits:PRIMARY",
    "create_requests:uk_create_request"
  };

  private static final String[] REQUIRED_INDEXES = {
    "punch_logs:idx_punch_logs_user_photo",
    "agent_media:idx_agent_media_cleanup_due",
    "feedback_reports:idx_feedback_owner"
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
    } else {
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
      try {
        DatabasePopulatorUtils.execute(populator, dataSource);
      } catch (Exception e) {
        log.warn("schema bootstrap script errors (continuing) errorCode={}", e.getClass().getSimpleName());
      }
    }

    List<String> missing = missingRequirements();
    if (!missing.isEmpty()) {
      throw new IllegalStateException("schema incomplete, missing requirements: " + missing
          + "; run npm run db:init:docker before starting API");
    }
    log.info("schema bootstrap: required tables, columns and indexes present");
  }

  private List<String> missingRequirements() {
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
      for (String entry : REQUIRED_COLUMNS) {
        String[] parts = entry.split(":", 2);
        try (ResultSet rs = st.executeQuery(
            "SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = '"
                + parts[0] + "' AND column_name = '" + parts[1] + "' LIMIT 1")) {
          if (!rs.next()) missing.add(entry);
        }
      }
      for (String entry : REQUIRED_UNIQUE_INDEXES) {
        String[] parts = entry.split(":", 2);
        try (ResultSet rs = st.executeQuery(
            "SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = '"
                + parts[0] + "' AND index_name = '" + parts[1] + "' AND non_unique = 0 LIMIT 1")) {
          if (!rs.next()) missing.add(entry);
        }
      }
      for (String entry : REQUIRED_INDEXES) {
        String[] parts = entry.split(":", 2);
        try (ResultSet rs = st.executeQuery(
            "SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = '"
                + parts[0] + "' AND index_name = '" + parts[1] + "' LIMIT 1")) {
          if (!rs.next()) missing.add(entry);
        }
      }
    } catch (Exception e) {
      log.error("schema bootstrap verification failed errorCode={}", e.getClass().getSimpleName());
      return List.of("(verification-failed)");
    }
    return missing;
  }
}
