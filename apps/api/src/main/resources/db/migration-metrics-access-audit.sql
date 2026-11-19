USE today;

CREATE TABLE IF NOT EXISTS metrics_access_audit (
  id VARCHAR(36) NOT NULL,
  endpoint VARCHAR(64) NOT NULL,
  from_date DATE NOT NULL,
  to_date DATE NOT NULL,
  platform VARCHAR(16) NULL,
  app_version VARCHAR(32) NULL,
  event_schema_version INT NULL,
  accessed_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_metrics_audit_accessed (accessed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
