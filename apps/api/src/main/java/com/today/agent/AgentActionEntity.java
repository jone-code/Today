package com.today.agent;

import java.time.Instant;

public class AgentActionEntity {
  private String proposalId;
  private String kind;
  private String targetId;
  private String summary;
  private String result;
  private Instant executedAt;
  private String undoSnapshotJson;
  private Instant undoneAt;

  public String getProposalId() { return proposalId; }
  public void setProposalId(String proposalId) { this.proposalId = proposalId; }
  public String getKind() { return kind; }
  public void setKind(String kind) { this.kind = kind; }
  public String getTargetId() { return targetId; }
  public void setTargetId(String targetId) { this.targetId = targetId; }
  public String getSummary() { return summary; }
  public void setSummary(String summary) { this.summary = summary; }
  public String getResult() { return result; }
  public void setResult(String result) { this.result = result; }
  public Instant getExecutedAt() { return executedAt; }
  public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
  public String getUndoSnapshotJson() { return undoSnapshotJson; }
  public void setUndoSnapshotJson(String undoSnapshotJson) { this.undoSnapshotJson = undoSnapshotJson; }
  public Instant getUndoneAt() { return undoneAt; }
  public void setUndoneAt(Instant undoneAt) { this.undoneAt = undoneAt; }
}
