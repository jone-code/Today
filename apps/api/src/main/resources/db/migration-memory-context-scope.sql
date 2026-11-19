-- Per-memory Chat context visibility (idempotent)
USE today;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'memories'
    AND COLUMN_NAME = 'chat_context_scope'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE memories ADD COLUMN chat_context_scope VARCHAR(32) NOT NULL DEFAULT ''chat_and_timeline'' AFTER archived',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE memories
SET chat_context_scope = 'chat_and_timeline'
WHERE chat_context_scope IS NULL OR chat_context_scope = '';
