-- Migration: todo + punch modules
USE today;

CREATE TABLE IF NOT EXISTS todos (
  id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  title VARCHAR(200) NOT NULL,
  note VARCHAR(1000) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'open',
  due_date DATE NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  completed_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  KEY idx_todos_user_status (user_id, status, updated_at DESC),
  KEY idx_todos_user_due (user_id, due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS punch_habits (
  id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  title VARCHAR(128) NOT NULL,
  description VARCHAR(512) NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_punch_habits_user (user_id, enabled, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS punch_logs (
  id VARCHAR(36) NOT NULL,
  habit_id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  punch_date DATE NOT NULL,
  note VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_punch_habit_date (habit_id, punch_date),
  KEY idx_punch_logs_user_date (user_id, punch_date DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
