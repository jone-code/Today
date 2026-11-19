package com.today.telemetry;

import java.time.Instant;

public class ProductEventEntity {
  private String id; private String userId; private String sessionId; private String eventName;
  private String platform; private Integer eventSchemaVersion; private Instant receivedAt;
  private String screen; private String entityType; private String entityId; private String propertiesJson;
  private Instant occurredAt; private String clientRequestId; private String appVersion;
  public String getId(){return id;} public void setId(String v){id=v;}
  public String getUserId(){return userId;} public void setUserId(String v){userId=v;}
  public String getSessionId(){return sessionId;} public void setSessionId(String v){sessionId=v;}
  public String getEventName(){return eventName;} public void setEventName(String v){eventName=v;}
  public String getPlatform(){return platform;} public void setPlatform(String v){platform=v;}
  public Integer getEventSchemaVersion(){return eventSchemaVersion;} public void setEventSchemaVersion(Integer v){eventSchemaVersion=v;}
  public Instant getReceivedAt(){return receivedAt;} public void setReceivedAt(Instant v){receivedAt=v;}
  public String getScreen(){return screen;} public void setScreen(String v){screen=v;}
  public String getEntityType(){return entityType;} public void setEntityType(String v){entityType=v;}
  public String getEntityId(){return entityId;} public void setEntityId(String v){entityId=v;}
  public String getPropertiesJson(){return propertiesJson;} public void setPropertiesJson(String v){propertiesJson=v;}
  public Instant getOccurredAt(){return occurredAt;} public void setOccurredAt(Instant v){occurredAt=v;}
  public String getClientRequestId(){return clientRequestId;} public void setClientRequestId(String v){clientRequestId=v;}
  public String getAppVersion(){return appVersion;} public void setAppVersion(String v){appVersion=v;}
}
