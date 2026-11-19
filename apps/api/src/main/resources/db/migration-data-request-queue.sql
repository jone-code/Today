USE today;
-- Completed requests must not prevent later exports of the same user's data.
SET @has_index := (SELECT COUNT(*) FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name='data_requests' AND index_name='uk_data_requests_active');
SET @sql := IF(@has_index>0, 'ALTER TABLE data_requests DROP INDEX uk_data_requests_active', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
