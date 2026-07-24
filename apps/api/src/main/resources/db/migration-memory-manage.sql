-- Soft-archive flag for user-managed memories (idempotent)
USE today;

SET @col_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'memories'
    AND COLUMN_NAME = 'archived'
);

SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE memories ADD COLUMN archived TINYINT(1) NOT NULL DEFAULT 0 AFTER strength, ADD KEY idx_memories_user_archived (user_id, archived, strength DESC, updated_at DESC)',
  'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
