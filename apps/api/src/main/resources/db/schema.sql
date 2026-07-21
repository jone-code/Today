CREATE DATABASE IF NOT EXISTS today
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE today;

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
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_memories_user_strength (user_id, strength DESC, updated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
