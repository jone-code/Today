package com.today.agent;

import java.time.Instant;

public class AppointmentEntity {
  private String id;
  private String userId;
  private String proposalId;
  private String sourceConversationId;
  private String sourceMessageId;
  private String title;
  private Instant dueAt;
  private String timezone;
  private String status;
  private Instant createdAt;
  private Instant deliveredAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getProposalId() { return proposalId; }
  public void setProposalId(String proposalId) { this.proposalId = proposalId; }
  public String getSourceConversationId() { return sourceConversationId; }
  public void setSourceConversationId(String sourceConversationId) { this.sourceConversationId = sourceConversationId; }
  public String getSourceMessageId() { return sourceMessageId; }
  public void setSourceMessageId(String sourceMessageId) { this.sourceMessageId = sourceMessageId; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public Instant getDueAt() { return dueAt; }
  public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
  public String getTimezone() { return timezone; }
  public void setTimezone(String timezone) { this.timezone = timezone; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getDeliveredAt() { return deliveredAt; }
  public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }
}
