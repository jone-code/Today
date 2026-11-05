package com.today.memory;

import java.time.LocalDate;

public class MemoryOrigin {
  private String sourceType;
  private String sourceId;
  private LocalDate checkinDate;
  private String conversationId;

  public String getSourceType() { return sourceType; }
  public void setSourceType(String sourceType) { this.sourceType = sourceType; }
  public String getSourceId() { return sourceId; }
  public void setSourceId(String sourceId) { this.sourceId = sourceId; }
  public LocalDate getCheckinDate() { return checkinDate; }
  public void setCheckinDate(LocalDate checkinDate) { this.checkinDate = checkinDate; }
  public String getConversationId() { return conversationId; }
  public void setConversationId(String conversationId) { this.conversationId = conversationId; }
}
