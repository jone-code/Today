package com.today.persistence;

import java.time.Instant;

public class AgentProposalEntity {
  private String id;
  private String userId;
  private String sourceMessageId;
  private String kind;
  private String payloadJson;
  private String status;
  private Instant expiresAt;
  private Instant createdAt;
  private Instant confirmedAt;
  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getSourceMessageId() { return sourceMessageId; }
  public void setSourceMessageId(String sourceMessageId) { this.sourceMessageId = sourceMessageId; }
  public String getKind() { return kind; }
  public void setKind(String kind) { this.kind = kind; }
  public String getPayloadJson() { return payloadJson; }
  public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getConfirmedAt() { return confirmedAt; }
  public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
}
