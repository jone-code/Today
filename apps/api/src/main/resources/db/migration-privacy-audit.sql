USE today;

CREATE TABLE IF NOT EXISTS user_privacy_settings (
  user_id VARCHAR(64) NOT NULL,
  chat_memory_enabled TINYINT(1) NOT NULL DEFAULT 1,
  voice_retention VARCHAR(32) NOT NULL DEFAULT 'delete_on_success',
  followup_enabled TINYINT(1) NOT NULL DEFAULT 1,
  version INT NOT NULL DEFAULT 1,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS memory_access_audit (
  id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  memory_id VARCHAR(128) NOT NULL,
  action VARCHAR(32) NOT NULL,
  source VARCHAR(32) NOT NULL,
  request_id VARCHAR(128) NULL,
  occurred_at DATETIME(3) NOT NULL,
  metadata_json TEXT NULL,
  PRIMARY KEY (id),
  KEY idx_memory_audit_user_time (user_id, occurred_at DESC),
  KEY idx_memory_audit_memory_time (memory_id, occurred_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
