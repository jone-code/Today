USE today;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'product_events' AND column_name = 'received_at') = 0,
  'ALTER TABLE product_events ADD COLUMN received_at DATETIME(3) NULL AFTER occurred_at',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'product_events' AND column_name = 'platform') = 0,
  "ALTER TABLE product_events ADD COLUMN platform VARCHAR(16) NOT NULL DEFAULT 'unknown' AFTER received_at",
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'product_events' AND column_name = 'event_schema_version') = 0,
  'ALTER TABLE product_events ADD COLUMN event_schema_version INT NOT NULL DEFAULT 1 AFTER platform',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE product_events SET received_at = occurred_at WHERE received_at IS NULL;
ALTER TABLE product_events MODIFY COLUMN received_at DATETIME(3) NOT NULL;
