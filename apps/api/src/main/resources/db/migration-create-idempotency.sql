USE today;

CREATE TABLE IF NOT EXISTS create_requests (
  user_id VARCHAR(64) NOT NULL,
  scope VARCHAR(32) NOT NULL,
  request_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
  request_hash CHAR(64) NOT NULL,
  response_json TEXT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_create_request (user_id, scope, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
