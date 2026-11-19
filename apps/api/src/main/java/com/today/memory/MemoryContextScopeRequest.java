package com.today.memory;

import jakarta.validation.constraints.NotBlank;

public class MemoryContextScopeRequest {
  @NotBlank
  private String scope;
  private String expectedUpdatedAt;

  public String getScope() { return scope; }
  public void setScope(String scope) { this.scope = scope; }
  public String getExpectedUpdatedAt() { return expectedUpdatedAt; }
  public void setExpectedUpdatedAt(String expectedUpdatedAt) { this.expectedUpdatedAt = expectedUpdatedAt; }
}
