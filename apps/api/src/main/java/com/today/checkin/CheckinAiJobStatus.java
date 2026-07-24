package com.today.checkin;

/** pending → running → succeeded | failed（可重试） */
public enum CheckinAiJobStatus {
  pending,
  running,
  succeeded,
  failed
}
