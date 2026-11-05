package com.today.agent;

public record AgentProposalDto(
    String id, String kind, String payloadJson, String status, String sourceMessageId, String createdAt) {}
