package com.today.persistence;

import java.time.Instant;

public class AiCallLogEntity {
  private String id;
  private String kind;
  private String task;
  private String provider;
  private String outcome;
  private int elapsedMs;
  private int inputUnits;
  private String errorMessage;
  private Instant createdAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public String getTask() {
    return task;
  }

  public void setTask(String task) {
    this.task = task;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getOutcome() {
    return outcome;
  }

  public void setOutcome(String outcome) {
    this.outcome = outcome;
  }

  public int getElapsedMs() {
    return elapsedMs;
  }

  public void setElapsedMs(int elapsedMs) {
    this.elapsedMs = elapsedMs;
  }

  public int getInputUnits() {
    return inputUnits;
  }

  public void setInputUnits(int inputUnits) {
    this.inputUnits = inputUnits;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
