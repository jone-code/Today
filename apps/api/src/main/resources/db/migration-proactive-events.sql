-- Proactive prompt lifecycle (shown / selected / dismissed / answered)
USE today;

CREATE TABLE IF NOT EXISTS proactive_prompt_events (
  id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  prompt_date DATE NOT NULL,
  prompt_id VARCHAR(64) NOT NULL,
  fingerprint VARCHAR(64) NOT NULL,
  source VARCHAR(16) NOT NULL,
  prompt_text VARCHAR(512) NOT NULL,
  related_date DATE NULL,
  status VARCHAR(16) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_proactive_user_fp_date (user_id, fingerprint, prompt_date),
  KEY idx_proactive_user_status (user_id, status, updated_at DESC),
  KEY idx_proactive_user_fp (user_id, fingerprint, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
