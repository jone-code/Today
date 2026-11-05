USE today;

CREATE TABLE IF NOT EXISTS vector_sync_jobs (
  memory_id VARCHAR(128) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  version BIGINT NOT NULL DEFAULT 1,
  state VARCHAR(16) NOT NULL DEFAULT 'pending',
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NOT NULL,
  lease_until DATETIME(3) NULL,
  last_error_code VARCHAR(80) NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (memory_id),
  KEY idx_vector_sync_due (state, next_attempt_at, lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_profiles (
  user_id VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL,
  tone VARCHAR(64) NOT NULL DEFAULT '温和、好奇、有分寸',
  avatar_media_id VARCHAR(36) NULL,
  version INT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_media (
  id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  relative_path VARCHAR(512) NOT NULL,
  content_type VARCHAR(64) NOT NULL,
  size_bytes BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  created_at DATETIME(3) NOT NULL,
  replaced_at DATETIME(3) NULL,
  next_cleanup_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  KEY idx_agent_media_user_status (user_id, status, created_at DESC),
  KEY idx_agent_media_cleanup_due (status, next_cleanup_at, replaced_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversations (
  id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  title VARCHAR(128) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  version INT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  KEY idx_conversations_user_updated (user_id, status, updated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversation_messages (
  id VARCHAR(36) NOT NULL,
  conversation_id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  role VARCHAR(16) NOT NULL,
  body TEXT NOT NULL,
  client_message_id VARCHAR(128) NULL,
  reply_to_message_id VARCHAR(36) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'succeeded',
  created_at DATETIME(3) NOT NULL,
  generation_started_at DATETIME(3) NULL,
  generation_attempt INT NOT NULL DEFAULT 1,
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_conversation_client_message (user_id, client_message_id),
  UNIQUE KEY uk_conversation_reply (reply_to_message_id),
  KEY idx_messages_conversation_created (conversation_id, deleted_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @today_generation_column_sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'conversation_messages'
   AND column_name = 'generation_started_at') = 0,
  'ALTER TABLE conversation_messages ADD COLUMN generation_started_at DATETIME(3) NULL',
  'SELECT 1'
);
PREPARE today_generation_stmt FROM @today_generation_column_sql;
EXECUTE today_generation_stmt;
DEALLOCATE PREPARE today_generation_stmt;

SET @today_attempt_column_sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'conversation_messages'
   AND column_name = 'generation_attempt') = 0,
  'ALTER TABLE conversation_messages ADD COLUMN generation_attempt INT NOT NULL DEFAULT 1',
  'SELECT 1'
);
PREPARE today_attempt_stmt FROM @today_attempt_column_sql;
EXECUTE today_attempt_stmt;
DEALLOCATE PREPARE today_attempt_stmt;

CREATE TABLE IF NOT EXISTS agent_proposals (
  id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  source_message_id VARCHAR(36) NOT NULL,
  kind VARCHAR(24) NOT NULL,
  payload_json TEXT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  expires_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  confirmed_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  KEY idx_agent_proposals_user_status (user_id, status, created_at DESC),
  UNIQUE KEY uk_agent_proposal_source_kind (source_message_id, kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_action_logs (
  proposal_id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  kind VARCHAR(24) NOT NULL,
  target_id VARCHAR(128) NOT NULL,
  summary VARCHAR(200) NOT NULL,
  result VARCHAR(16) NOT NULL,
  executed_at DATETIME(3) NOT NULL,
  undo_snapshot_json TEXT NULL,
  undone_at DATETIME(3) NULL,
  PRIMARY KEY (proposal_id),
  KEY idx_agent_actions_user (user_id, executed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS memory_sources (
  memory_id VARCHAR(128) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id VARCHAR(64) NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (memory_id, source_type, source_id),
  KEY idx_memory_sources_source (user_id, source_type, source_id, active),
  KEY idx_memory_sources_memory (memory_id, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS checkin_memory_candidates (
  id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  checkin_id VARCHAR(36) NOT NULL,
  source_hash CHAR(64) NOT NULL,
  ordinal_index SMALLINT NOT NULL,
  category VARCHAR(32) NOT NULL,
  memory_text VARCHAR(512) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  created_at DATETIME(3) NOT NULL,
  confirmed_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_checkin_candidate_version (checkin_id, source_hash, ordinal_index),
  KEY idx_checkin_candidates_user (user_id, checkin_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS followup_topics (
  id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  source_conversation_id VARCHAR(36) NOT NULL,
  source_message_id VARCHAR(36) NOT NULL,
  reason VARCHAR(512) NOT NULL,
  state VARCHAR(16) NOT NULL DEFAULT 'authorized',
  next_eligible_at DATETIME(3) NOT NULL,
  unanswered_count INT NOT NULL DEFAULT 0,
  last_delivered_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_followup_due (state, next_eligible_at),
  KEY idx_followup_user (user_id, state, updated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS followup_preferences (
  user_id VARCHAR(64) NOT NULL,
  timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
  max_per_day INT NOT NULL DEFAULT 1,
  max_per_week INT NOT NULL DEFAULT 3,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS one_off_appointments (
  id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  proposal_id VARCHAR(36) NOT NULL,
  source_conversation_id VARCHAR(36) NOT NULL,
  source_message_id VARCHAR(36) NOT NULL,
  title VARCHAR(200) NOT NULL,
  due_at_utc DATETIME(3) NOT NULL,
  original_timezone VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  created_at DATETIME(3) NOT NULL,
  delivered_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_one_off_proposal (proposal_id),
  KEY idx_one_off_due (status, due_at_utc),
  KEY idx_one_off_user (user_id, status, due_at_utc)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inbox_items (
  id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  kind VARCHAR(24) NOT NULL,
  source_kind VARCHAR(24) NOT NULL,
  source_id VARCHAR(36) NOT NULL,
  body VARCHAR(512) NOT NULL,
  scheduled_at DATETIME(3) NOT NULL,
  state VARCHAR(16) NOT NULL DEFAULT 'unread',
  created_at DATETIME(3) NOT NULL,
  read_at DATETIME(3) NULL,
  snoozed_until DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_inbox_source_schedule (user_id, source_kind, source_id, scheduled_at),
  KEY idx_inbox_user_state (user_id, state, scheduled_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
