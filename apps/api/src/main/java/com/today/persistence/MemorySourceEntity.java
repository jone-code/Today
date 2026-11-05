package com.today.persistence;

import java.time.Instant;

public class MemorySourceEntity {
  private String memoryId;
  private String userId;
  private String sourceType;
  private String sourceId;
  private boolean active;
  private Instant createdAt;
  public String getMemoryId() { return memoryId; }
  public void setMemoryId(String memoryId) { this.memoryId = memoryId; }
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getSourceType() { return sourceType; }
  public void setSourceType(String sourceType) { this.sourceType = sourceType; }
  public String getSourceId() { return sourceId; }
  public void setSourceId(String sourceId) { this.sourceId = sourceId; }
  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
