-- AI call audit / observability
USE today;

CREATE TABLE IF NOT EXISTS ai_call_logs (
  id VARCHAR(36) NOT NULL,
  kind VARCHAR(16) NOT NULL COMMENT 'complete|embed',
  task VARCHAR(32) NULL COMMENT 'summary|memory_extract|proactive|embed',
  provider VARCHAR(16) NOT NULL,
  outcome VARCHAR(16) NOT NULL COMMENT 'ok|fallback|failed|skipped',
  elapsed_ms INT NOT NULL,
  input_units INT NOT NULL DEFAULT 0 COMMENT 'texts count or approx size',
  error_message VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_ai_call_logs_created (created_at DESC),
  KEY idx_ai_call_logs_task_outcome (task, outcome, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
