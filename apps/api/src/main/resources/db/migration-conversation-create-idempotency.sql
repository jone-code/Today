USE today;

SET @has_column := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'conversations'
    AND column_name = 'client_request_id'
);
SET @sql := IF(@has_column = 0,
  'ALTER TABLE conversations ADD COLUMN client_request_id VARCHAR(128) NULL',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_index := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'conversations'
    AND index_name = 'uk_conversation_client_request'
);
SET @sql := IF(@has_index = 0,
  'ALTER TABLE conversations ADD UNIQUE KEY uk_conversation_client_request (user_id, client_request_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
