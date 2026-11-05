USE today;

SET @today_media_cleanup_column = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'agent_media'
   AND column_name = 'next_cleanup_at') = 0,
  'ALTER TABLE agent_media ADD COLUMN next_cleanup_at DATETIME(3) NULL',
  'SELECT 1'
);
PREPARE today_media_cleanup_column_stmt FROM @today_media_cleanup_column;
EXECUTE today_media_cleanup_column_stmt;
DEALLOCATE PREPARE today_media_cleanup_column_stmt;

SET @today_media_cleanup_index = IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'agent_media'
   AND index_name = 'idx_agent_media_cleanup_due') = 0,
  'ALTER TABLE agent_media ADD INDEX idx_agent_media_cleanup_due (status, next_cleanup_at, replaced_at)',
  'SELECT 1'
);
PREPARE today_media_cleanup_index_stmt FROM @today_media_cleanup_index;
EXECUTE today_media_cleanup_index_stmt;
DEALLOCATE PREPARE today_media_cleanup_index_stmt;
