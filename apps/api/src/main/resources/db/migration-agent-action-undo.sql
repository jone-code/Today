USE today;

SET @today_action_undo_snapshot = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'agent_action_logs'
   AND column_name = 'undo_snapshot_json') = 0,
  'ALTER TABLE agent_action_logs ADD COLUMN undo_snapshot_json TEXT NULL',
  'SELECT 1'
);
PREPARE today_action_undo_snapshot_stmt FROM @today_action_undo_snapshot;
EXECUTE today_action_undo_snapshot_stmt;
DEALLOCATE PREPARE today_action_undo_snapshot_stmt;

SET @today_action_undone_at = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'agent_action_logs'
   AND column_name = 'undone_at') = 0,
  'ALTER TABLE agent_action_logs ADD COLUMN undone_at DATETIME(3) NULL',
  'SELECT 1'
);
PREPARE today_action_undone_at_stmt FROM @today_action_undone_at;
EXECUTE today_action_undone_at_stmt;
DEALLOCATE PREPARE today_action_undone_at_stmt;
