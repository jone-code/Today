package com.today.persistence;

import java.time.Instant;

public class ConversationEntity {
  private String id;
  private String userId;
  private String clientRequestId;
  private String title;
  private String status;
  private int version;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant deletedAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getClientRequestId() { return clientRequestId; }
  public void setClientRequestId(String clientRequestId) { this.clientRequestId = clientRequestId; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public int getVersion() { return version; }
  public void setVersion(int version) { this.version = version; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
  public Instant getDeletedAt() { return deletedAt; }
  public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
