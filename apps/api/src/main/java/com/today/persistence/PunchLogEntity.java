package com.today.persistence;

import java.time.Instant;
import java.time.LocalDate;

public class PunchLogEntity {
  private String id;
  private String habitId;
  private String userId;
  private LocalDate punchDate;
  private String note;
  private String photoPath;
  private Instant createdAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getHabitId() {
    return habitId;
  }

  public void setHabitId(String habitId) {
    this.habitId = habitId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public LocalDate getPunchDate() {
    return punchDate;
  }

  public void setPunchDate(LocalDate punchDate) {
    this.punchDate = punchDate;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public String getPhotoPath() {
    return photoPath;
  }

  public void setPhotoPath(String photoPath) {
    this.photoPath = photoPath;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
