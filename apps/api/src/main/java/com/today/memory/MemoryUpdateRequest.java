package com.today.memory;

import com.today.common.MemoryCategory;
import jakarta.validation.constraints.Size;

public class MemoryUpdateRequest {
  @Size(max = 512)
  private String text;

  private MemoryCategory category;

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public MemoryCategory getCategory() {
    return category;
  }

  public void setCategory(MemoryCategory category) {
    this.category = category;
  }
}
