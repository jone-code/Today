-- Add embedding storage for retrieval-based proactive (existing DBs)
-- Idempotent: safe to re-run if column already exists
USE today;

SET @col_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'memories'
    AND COLUMN_NAME = 'embedding_json'
);

SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE memories ADD COLUMN embedding_json LONGTEXT NULL AFTER strength',
  'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
