USE today;

CREATE TABLE IF NOT EXISTS privacy_settings_audit (
  id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  field VARCHAR(32) NOT NULL,
  old_value VARCHAR(32) NOT NULL,
  new_value VARCHAR(32) NOT NULL,
  source VARCHAR(32) NOT NULL,
  occurred_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_privacy_audit_user_time (user_id, occurred_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
