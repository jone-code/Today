CREATE TABLE IF NOT EXISTS weekly_reviews (
  id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  week_start DATE NOT NULL,
  status VARCHAR(16) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  body_json TEXT NULL,
  last_error VARCHAR(512) NULL,
  locked_at DATETIME(3) NULL,
  generated_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_weekly_reviews_user_week (user_id, week_start),
  KEY idx_weekly_reviews_status (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
