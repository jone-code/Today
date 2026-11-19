USE today;

USE today;

SET @has_index := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'product_events'
    AND index_name = 'uk_product_events_request'
);
SET @sql := IF(@has_index = 0,
  'ALTER TABLE product_events ADD UNIQUE KEY uk_product_events_request (user_id, client_request_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
