package com.today.agent;

public record AgentProfileDto(
    String userId, String name, String tone, String avatarMediaId, String avatarUrl, int version) {}
