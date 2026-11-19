package com.today.agent;

public record AgentProposalDto(
    String id, String kind, String payloadJson, String status, String sourceMessageId,
    String createdAt, String expiresAt) {
  public AgentProposalDto(String id, String kind, String payloadJson, String status,
      String sourceMessageId, String createdAt) {
    this(id, kind, payloadJson, status, sourceMessageId, createdAt, null);
  }
}
