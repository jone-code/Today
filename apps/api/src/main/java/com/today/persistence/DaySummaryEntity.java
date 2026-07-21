package com.today.persistence;

import java.time.Instant;
import java.time.LocalDate;

public class DaySummaryEntity {
  private String checkinId;
  private String userId;
  private LocalDate summaryDate;
  private String completedJson;
  private String mood;
  private String moodLabel;
  private String keywordsJson;
  private String oneLiner;
  private String highlight;
  private String provider;
  private Instant createdAt;

  public String getCheckinId() {
    return checkinId;
  }

  public void setCheckinId(String checkinId) {
    this.checkinId = checkinId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public LocalDate getSummaryDate() {
    return summaryDate;
  }

  public void setSummaryDate(LocalDate summaryDate) {
    this.summaryDate = summaryDate;
  }

  public String getCompletedJson() {
    return completedJson;
  }

  public void setCompletedJson(String completedJson) {
    this.completedJson = completedJson;
  }

  public String getMood() {
    return mood;
  }

  public void setMood(String mood) {
    this.mood = mood;
  }

  public String getMoodLabel() {
    return moodLabel;
  }

  public void setMoodLabel(String moodLabel) {
    this.moodLabel = moodLabel;
  }

  public String getKeywordsJson() {
    return keywordsJson;
  }

  public void setKeywordsJson(String keywordsJson) {
    this.keywordsJson = keywordsJson;
  }

  public String getOneLiner() {
    return oneLiner;
  }

  public void setOneLiner(String oneLiner) {
    this.oneLiner = oneLiner;
  }

  public String getHighlight() {
    return highlight;
  }

  public void setHighlight(String highlight) {
    this.highlight = highlight;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
