CREATE TABLE IF NOT EXISTS punch_photo_cleanup_jobs (
  path VARCHAR(512) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NOT NULL,
  lease_until DATETIME(3) NULL,
  PRIMARY KEY (path),
  KEY idx_punch_photo_cleanup_due (next_attempt_at, lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @photo_index_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'punch_logs'
    AND INDEX_NAME = 'idx_punch_logs_user_photo'
);

SET @photo_index_sql := IF(
  @photo_index_exists = 0,
  'ALTER TABLE punch_logs ADD INDEX idx_punch_logs_user_photo (user_id, photo_path(191))',
  'SELECT 1'
);

PREPARE photo_index_stmt FROM @photo_index_sql;
EXECUTE photo_index_stmt;
DEALLOCATE PREPARE photo_index_stmt;
