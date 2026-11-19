package com.today.voice;

import java.time.Instant;

public class VoiceTranscriptionJobEntity {
  private String id; private String userId; private String clientRequestId; private String status;
  private String contentType; private long durationMs; private String payloadHash; private String mediaPath; private String transcript; private String errorCode;
  private int attempts; private Instant expiresAt; private Instant createdAt; private Instant updatedAt; private Instant completedAt;
  public String getId() { return id; } public void setId(String v) { id = v; }
  public String getUserId() { return userId; } public void setUserId(String v) { userId = v; }
  public String getClientRequestId() { return clientRequestId; } public void setClientRequestId(String v) { clientRequestId = v; }
  public String getStatus() { return status; } public void setStatus(String v) { status = v; }
  public String getContentType() { return contentType; } public void setContentType(String v) { contentType = v; }
  public long getDurationMs() { return durationMs; } public void setDurationMs(long v) { durationMs = v; }
  public String getPayloadHash() { return payloadHash; } public void setPayloadHash(String v) { payloadHash = v; }
  public String getMediaPath() { return mediaPath; } public void setMediaPath(String v) { mediaPath = v; }
  public String getTranscript() { return transcript; } public void setTranscript(String v) { transcript = v; }
  public String getErrorCode() { return errorCode; } public void setErrorCode(String v) { errorCode = v; }
  public int getAttempts() { return attempts; } public void setAttempts(int v) { attempts = v; }
  public Instant getExpiresAt() { return expiresAt; } public void setExpiresAt(Instant v) { expiresAt = v; }
  public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { createdAt = v; }
  public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant v) { updatedAt = v; }
  public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant v) { completedAt = v; }
}
