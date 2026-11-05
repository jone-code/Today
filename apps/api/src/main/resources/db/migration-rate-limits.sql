CREATE TABLE IF NOT EXISTS api_rate_limits (
  scope VARCHAR(32) NOT NULL,
  subject_hash CHAR(64) NOT NULL,
  window_start DATETIME(3) NOT NULL,
  hit_count INT NOT NULL,
  PRIMARY KEY (scope, subject_hash, window_start),
  KEY idx_rate_limits_window (window_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
