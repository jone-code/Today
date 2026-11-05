package com.today.punch;

import java.time.Instant;

public class PunchPhotoCleanupJob {
  private String path;
  private String userId;
  private int attempts;
  private Instant leaseUntil;

  public String getPath() { return path; }
  public void setPath(String path) { this.path = path; }
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public int getAttempts() { return attempts; }
  public void setAttempts(int attempts) { this.attempts = attempts; }
  public Instant getLeaseUntil() { return leaseUntil; }
  public void setLeaseUntil(Instant leaseUntil) { this.leaseUntil = leaseUntil; }
}
