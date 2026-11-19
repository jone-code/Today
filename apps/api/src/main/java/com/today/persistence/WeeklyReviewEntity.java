package com.today.persistence;

import java.time.Instant;
import java.time.LocalDate;

public class WeeklyReviewEntity {
  private String id;
  private String userId;
  private LocalDate weekStart;
  private String status;
  private int attempts;
  private String bodyJson;
  private String lastError;
  private Instant lockedAt;
  private Instant generatedAt;
  private Instant createdAt;
  private Instant updatedAt;

  public String getId() { return id; }
  public void setId(String value) { id = value; }
  public String getUserId() { return userId; }
  public void setUserId(String value) { userId = value; }
  public LocalDate getWeekStart() { return weekStart; }
  public void setWeekStart(LocalDate value) { weekStart = value; }
  public String getStatus() { return status; }
  public void setStatus(String value) { status = value; }
  public int getAttempts() { return attempts; }
  public void setAttempts(int value) { attempts = value; }
  public String getBodyJson() { return bodyJson; }
  public void setBodyJson(String value) { bodyJson = value; }
  public String getLastError() { return lastError; }
  public void setLastError(String value) { lastError = value; }
  public Instant getLockedAt() { return lockedAt; }
  public void setLockedAt(Instant value) { lockedAt = value; }
  public Instant getGeneratedAt() { return generatedAt; }
  public void setGeneratedAt(Instant value) { generatedAt = value; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant value) { createdAt = value; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant value) { updatedAt = value; }
}
