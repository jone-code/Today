-- Durable checkin AI pipeline jobs (retry + failure visibility)
USE today;

CREATE TABLE IF NOT EXISTS checkin_ai_jobs (
  id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  checkin_id VARCHAR(36) NOT NULL,
  checkin_date DATE NOT NULL,
  status VARCHAR(16) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  max_attempts INT NOT NULL DEFAULT 5,
  last_error VARCHAR(512) NULL,
  next_run_at DATETIME(3) NOT NULL,
  locked_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_checkin_ai_jobs_checkin (checkin_id),
  KEY idx_checkin_ai_jobs_due (status, next_run_at),
  KEY idx_checkin_ai_jobs_user_date (user_id, checkin_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
