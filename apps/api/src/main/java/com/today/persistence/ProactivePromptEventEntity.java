package com.today.persistence;

import java.time.Instant;
import java.time.LocalDate;

public class ProactivePromptEventEntity {
  private String id;
  private String userId;
  private LocalDate promptDate;
  private String promptId;
  private String fingerprint;
  private String source;
  private String promptText;
  private LocalDate relatedDate;
  private String status;
  private Instant createdAt;
  private Instant updatedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public LocalDate getPromptDate() {
    return promptDate;
  }

  public void setPromptDate(LocalDate promptDate) {
    this.promptDate = promptDate;
  }

  public String getPromptId() {
    return promptId;
  }

  public void setPromptId(String promptId) {
    this.promptId = promptId;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getPromptText() {
    return promptText;
  }

  public void setPromptText(String promptText) {
    this.promptText = promptText;
  }

  public LocalDate getRelatedDate() {
    return relatedDate;
  }

  public void setRelatedDate(LocalDate relatedDate) {
    this.relatedDate = relatedDate;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
