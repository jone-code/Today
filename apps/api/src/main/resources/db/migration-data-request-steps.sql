USE today;
SET @has_step := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='data_requests' AND column_name='step');
SET @sql := IF(@has_step=0, 'ALTER TABLE data_requests ADD COLUMN step INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_token_expiry := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='data_requests' AND column_name='download_token_expires_at');
SET @sql := IF(@has_token_expiry=0, 'ALTER TABLE data_requests ADD COLUMN download_token_expires_at DATETIME(3) NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Retire legacy plaintext packages; the cleanup worker removes them and users can export again.
UPDATE data_requests SET status='expired',expires_at=UTC_TIMESTAMP(3),download_token_hash=NULL
WHERE type='export' AND output_path LIKE '%.zip';
