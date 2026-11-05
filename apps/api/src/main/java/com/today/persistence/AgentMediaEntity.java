package com.today.persistence;

import java.time.Instant;

public class AgentMediaEntity {
  private String id;
  private String userId;
  private String relativePath;
  private String contentType;
  private long sizeBytes;
  private String status;
  private Instant createdAt;
  private Instant replacedAt;
  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getRelativePath() { return relativePath; }
  public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
  public String getContentType() { return contentType; }
  public void setContentType(String contentType) { this.contentType = contentType; }
  public long getSizeBytes() { return sizeBytes; }
  public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getReplacedAt() { return replacedAt; }
  public void setReplacedAt(Instant replacedAt) { this.replacedAt = replacedAt; }
}
