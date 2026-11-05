USE today;

CREATE TABLE IF NOT EXISTS feedback_reports (
  id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  kind VARCHAR(16) NOT NULL,
  body VARCHAR(1000) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  KEY idx_feedback_owner (user_id, deleted_at, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
