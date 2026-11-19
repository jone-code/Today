USE today;
CREATE TABLE IF NOT EXISTS voice_transcription_jobs (
  id VARCHAR(36) NOT NULL, user_id VARCHAR(64) NOT NULL, client_request_id VARCHAR(128) NOT NULL,
  status VARCHAR(24) NOT NULL, content_type VARCHAR(80) NOT NULL, duration_ms BIGINT NOT NULL DEFAULT 0, payload_hash CHAR(64) NULL, media_path VARCHAR(255) NULL,
  transcript TEXT NULL, error_code VARCHAR(80) NULL, attempts INT NOT NULL DEFAULT 0,
  expires_at DATETIME(3) NOT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, completed_at DATETIME(3) NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_voice_transcription_request (user_id, client_request_id),
  KEY idx_voice_transcription_status (status, updated_at), CONSTRAINT fk_voice_transcription_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET @has_column := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'voice_transcription_jobs' AND column_name = 'media_path'
);
SET @sql := IF(@has_column = 0,
  'ALTER TABLE voice_transcription_jobs ADD COLUMN media_path VARCHAR(255) NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_column := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'voice_transcription_jobs' AND column_name = 'payload_hash'
);
SET @sql := IF(@has_column = 0,
  'ALTER TABLE voice_transcription_jobs ADD COLUMN payload_hash CHAR(64) NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_index := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'voice_transcription_jobs'
    AND index_name = 'uk_voice_transcription_request'
);
SET @sql := IF(@has_index = 0,
  'ALTER TABLE voice_transcription_jobs ADD UNIQUE KEY uk_voice_transcription_request (user_id, client_request_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_index := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'voice_transcription_jobs'
    AND index_name = 'idx_voice_transcription_status'
);
SET @sql := IF(@has_index = 0,
  'ALTER TABLE voice_transcription_jobs ADD KEY idx_voice_transcription_status (status, updated_at)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
