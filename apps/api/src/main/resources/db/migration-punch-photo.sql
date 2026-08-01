-- Punch log photo path (local media key). Idempotent.
USE today;

SET @col_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'punch_logs'
    AND COLUMN_NAME = 'photo_path'
);

SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE punch_logs ADD COLUMN photo_path VARCHAR(512) NULL AFTER note',
  'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
