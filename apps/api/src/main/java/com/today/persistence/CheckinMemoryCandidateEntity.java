package com.today.persistence;

import java.time.Instant;

public class CheckinMemoryCandidateEntity {
  private String id;
  private String userId;
  private String checkinId;
  private String sourceHash;
  private int ordinalIndex;
  private String category;
  private String memoryText;
  private String status;
  private Instant createdAt;
  private Instant confirmedAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getCheckinId() { return checkinId; }
  public void setCheckinId(String checkinId) { this.checkinId = checkinId; }
  public String getSourceHash() { return sourceHash; }
  public void setSourceHash(String sourceHash) { this.sourceHash = sourceHash; }
  public int getOrdinalIndex() { return ordinalIndex; }
  public void setOrdinalIndex(int ordinalIndex) { this.ordinalIndex = ordinalIndex; }
  public String getCategory() { return category; }
  public void setCategory(String category) { this.category = category; }
  public String getMemoryText() { return memoryText; }
  public void setMemoryText(String memoryText) { this.memoryText = memoryText; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getConfirmedAt() { return confirmedAt; }
  public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
}
