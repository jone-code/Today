USE today;
CREATE TABLE IF NOT EXISTS data_requests (
  id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  type VARCHAR(24) NOT NULL,
  status VARCHAR(16) NOT NULL,
  requested_at DATETIME(3) NOT NULL,
  started_at DATETIME(3) NULL,
  completed_at DATETIME(3) NULL,
  expires_at DATETIME(3) NULL,
  attempts INT NOT NULL DEFAULT 0,
  error_code VARCHAR(64) NULL,
  output_path VARCHAR(512) NULL,
  download_token_hash CHAR(64) NULL,
  PRIMARY KEY (id),
  KEY idx_data_requests_user (user_id, requested_at DESC),
  UNIQUE KEY uk_data_requests_active (user_id, type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
