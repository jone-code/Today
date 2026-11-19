package com.today.privacy;

import jakarta.validation.constraints.NotNull;

public class PrivacySettingsRequest {
  @NotNull @jakarta.validation.constraints.Min(1) private Integer version;
  public Integer getVersion() { return version; }
  public void setVersion(Integer value) { version = value; }
  @NotNull private Boolean chatMemoryEnabled;
  private String voiceRetention;
  @NotNull private Boolean followupEnabled;
  public Boolean getChatMemoryEnabled() { return chatMemoryEnabled; }
  public void setChatMemoryEnabled(Boolean value) { chatMemoryEnabled = value; }
  public String getVoiceRetention() { return voiceRetention; }
  public void setVoiceRetention(String value) { voiceRetention = value; }
  public Boolean getFollowupEnabled() { return followupEnabled; }
  public void setFollowupEnabled(Boolean value) { followupEnabled = value; }
}
