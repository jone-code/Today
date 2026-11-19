USE today;

CREATE TABLE IF NOT EXISTS product_events (
  id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  session_id VARCHAR(128) NULL,
  event_name VARCHAR(80) NOT NULL,
  screen VARCHAR(40) NULL,
  entity_type VARCHAR(40) NULL,
  entity_id VARCHAR(128) NULL,
  properties_json TEXT NULL,
  occurred_at DATETIME(3) NOT NULL,
  received_at DATETIME(3) NOT NULL,
  platform VARCHAR(16) NOT NULL DEFAULT 'unknown',
  event_schema_version INT NOT NULL DEFAULT 1,
  client_request_id VARCHAR(128) NULL,
  app_version VARCHAR(32) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_events_request (user_id, client_request_id),
  KEY idx_product_events_user_time (user_id, occurred_at DESC),
  KEY idx_product_events_name_time (event_name, occurred_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
