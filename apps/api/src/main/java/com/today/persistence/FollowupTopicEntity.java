package com.today.persistence;

import java.time.Instant;

public class FollowupTopicEntity {
  private String id; private String userId; private String sourceConversationId; private String sourceMessageId;
  private String reason; private String state; private Instant nextEligibleAt; private int unansweredCount;
  private Instant lastDeliveredAt; private Instant createdAt; private Instant updatedAt;
  public String getId(){return id;} public void setId(String v){id=v;}
  public String getUserId(){return userId;} public void setUserId(String v){userId=v;}
  public String getSourceConversationId(){return sourceConversationId;} public void setSourceConversationId(String v){sourceConversationId=v;}
  public String getSourceMessageId(){return sourceMessageId;} public void setSourceMessageId(String v){sourceMessageId=v;}
  public String getReason(){return reason;} public void setReason(String v){reason=v;}
  public String getState(){return state;} public void setState(String v){state=v;}
  public Instant getNextEligibleAt(){return nextEligibleAt;} public void setNextEligibleAt(Instant v){nextEligibleAt=v;}
  public int getUnansweredCount(){return unansweredCount;} public void setUnansweredCount(int v){unansweredCount=v;}
  public Instant getLastDeliveredAt(){return lastDeliveredAt;} public void setLastDeliveredAt(Instant v){lastDeliveredAt=v;}
  public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
  public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
