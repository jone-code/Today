package com.today.privacy;

import java.time.Instant;

public class PrivacySettingsEntity {
  private String userId; private boolean chatMemoryEnabled; private String voiceRetention;
  private boolean followupEnabled; private int version; private Instant updatedAt;
  public String getUserId() { return userId; } public void setUserId(String v) { userId = v; }
  public boolean isChatMemoryEnabled() { return chatMemoryEnabled; } public void setChatMemoryEnabled(boolean v) { chatMemoryEnabled = v; }
  public String getVoiceRetention() { return voiceRetention; } public void setVoiceRetention(String v) { voiceRetention = v; }
  public boolean isFollowupEnabled() { return followupEnabled; } public void setFollowupEnabled(boolean v) { followupEnabled = v; }
  public int getVersion() { return version; } public void setVersion(int v) { version = v; }
  public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant v) { updatedAt = v; }
}
