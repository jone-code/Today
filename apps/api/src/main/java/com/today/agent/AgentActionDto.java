package com.today.agent;

public record AgentActionDto(String proposalId, String kind, String targetId, String summary,
    String result, String executedAt, boolean undoable, String undoneAt) {}
