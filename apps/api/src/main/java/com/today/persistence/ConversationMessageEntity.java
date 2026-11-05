package com.today.persistence;

import java.time.Instant;

public class ConversationMessageEntity {
  private String id;
  private String conversationId;
  private String userId;
  private String role;
  private String body;
  private String clientMessageId;
  private String replyToMessageId;
  private String status;
  private int generationAttempt;
  private Instant createdAt;
  private Instant deletedAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getConversationId() { return conversationId; }
  public void setConversationId(String conversationId) { this.conversationId = conversationId; }
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }
  public String getBody() { return body; }
  public void setBody(String body) { this.body = body; }
  public String getClientMessageId() { return clientMessageId; }
  public void setClientMessageId(String clientMessageId) { this.clientMessageId = clientMessageId; }
  public String getReplyToMessageId() { return replyToMessageId; }
  public void setReplyToMessageId(String replyToMessageId) { this.replyToMessageId = replyToMessageId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public int getGenerationAttempt() { return generationAttempt; }
  public void setGenerationAttempt(int generationAttempt) { this.generationAttempt = generationAttempt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getDeletedAt() { return deletedAt; }
  public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
