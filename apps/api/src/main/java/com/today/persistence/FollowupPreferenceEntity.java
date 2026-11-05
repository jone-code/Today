package com.today.persistence;

import java.time.Instant;

public class FollowupPreferenceEntity {
  private String userId; private String timezone; private int maxPerDay; private int maxPerWeek; private boolean enabled; private Instant updatedAt;
  public String getUserId(){return userId;} public void setUserId(String v){userId=v;}
  public String getTimezone(){return timezone;} public void setTimezone(String v){timezone=v;}
  public int getMaxPerDay(){return maxPerDay;} public void setMaxPerDay(int v){maxPerDay=v;}
  public int getMaxPerWeek(){return maxPerWeek;} public void setMaxPerWeek(int v){maxPerWeek=v;}
  public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
  public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
