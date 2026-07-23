package com.today.persistence;

import java.time.Instant;

public class MemoryEntity {
  private String id;
  private String userId;
  private String category;
  private String memoryText;
  private int strength;
  private String embeddingJson;
  private Instant updatedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getMemoryText() {
    return memoryText;
  }

  public void setMemoryText(String memoryText) {
    this.memoryText = memoryText;
  }

  public int getStrength() {
    return strength;
  }

  public void setStrength(int strength) {
    this.strength = strength;
  }

  public String getEmbeddingJson() {
    return embeddingJson;
  }

  public void setEmbeddingJson(String embeddingJson) {
    this.embeddingJson = embeddingJson;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
