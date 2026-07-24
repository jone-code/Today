CREATE DATABASE IF NOT EXISTS today
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE today;

CREATE TABLE IF NOT EXISTS users (
  id VARCHAR(36) NOT NULL,
  email VARCHAR(191) NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS checkins (
  id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  checkin_date DATE NOT NULL,
  raw_text TEXT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_checkins_user_date (user_id, checkin_date),
  KEY idx_checkins_user_date_desc (user_id, checkin_date DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS day_summaries (
  checkin_id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  summary_date DATE NOT NULL,
  completed_json TEXT NOT NULL,
  mood VARCHAR(16) NOT NULL,
  mood_label VARCHAR(32) NOT NULL,
  keywords_json TEXT NOT NULL,
  one_liner TEXT NOT NULL,
  highlight TEXT NOT NULL,
  provider VARCHAR(16) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (checkin_id),
  UNIQUE KEY uk_summaries_user_date (user_id, summary_date),
  KEY idx_summaries_user_date_desc (user_id, summary_date DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS memories (
  id VARCHAR(128) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  category VARCHAR(16) NOT NULL,
  memory_text VARCHAR(512) NOT NULL,
  strength INT NOT NULL DEFAULT 1,
  archived TINYINT(1) NOT NULL DEFAULT 0,
  embedding_json LONGTEXT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_memories_user_strength (user_id, strength DESC, updated_at DESC),
  KEY idx_memories_user_archived (user_id, archived, strength DESC, updated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reminders (
  id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  title VARCHAR(128) NOT NULL,
  message VARCHAR(512) NOT NULL,
  remind_time CHAR(5) NOT NULL COMMENT 'HH:mm in user timezone',
  timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_reminders_user_enabled (user_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reminder_deliveries (
  id VARCHAR(36) NOT NULL,
  reminder_id VARCHAR(36) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  fire_date DATE NOT NULL,
  title VARCHAR(128) NOT NULL,
  message VARCHAR(512) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  created_at DATETIME(3) NOT NULL,
  read_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_reminder_fire (reminder_id, fire_date),
  KEY idx_deliveries_user_status (user_id, status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
