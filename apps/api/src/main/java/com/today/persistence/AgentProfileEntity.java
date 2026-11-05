package com.today.persistence;

import java.time.Instant;

public class AgentProfileEntity {
  private String userId;
  private String name;
  private String tone;
  private String avatarMediaId;
  private int version;
  private Instant createdAt;
  private Instant updatedAt;

  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getTone() { return tone; }
  public void setTone(String tone) { this.tone = tone; }
  public String getAvatarMediaId() { return avatarMediaId; }
  public void setAvatarMediaId(String avatarMediaId) { this.avatarMediaId = avatarMediaId; }
  public int getVersion() { return version; }
  public void setVersion(int version) { this.version = version; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
