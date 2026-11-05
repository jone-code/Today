package com.today.persistence;

import java.time.Instant;

public class InboxItemEntity {
  private String id; private String userId; private String kind; private String sourceKind; private String sourceId;
  private String body; private Instant scheduledAt; private String state; private Instant createdAt; private Instant readAt; private Instant snoozedUntil;
  public String getId(){return id;} public void setId(String v){id=v;}
  public String getUserId(){return userId;} public void setUserId(String v){userId=v;}
  public String getKind(){return kind;} public void setKind(String v){kind=v;}
  public String getSourceKind(){return sourceKind;} public void setSourceKind(String v){sourceKind=v;}
  public String getSourceId(){return sourceId;} public void setSourceId(String v){sourceId=v;}
  public String getBody(){return body;} public void setBody(String v){body=v;}
  public Instant getScheduledAt(){return scheduledAt;} public void setScheduledAt(Instant v){scheduledAt=v;}
  public String getState(){return state;} public void setState(String v){state=v;}
  public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
  public Instant getReadAt(){return readAt;} public void setReadAt(Instant v){readAt=v;}
  public Instant getSnoozedUntil(){return snoozedUntil;} public void setSnoozedUntil(Instant v){snoozedUntil=v;}
}
