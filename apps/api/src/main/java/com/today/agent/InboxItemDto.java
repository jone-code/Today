package com.today.agent;

public record InboxItemDto(String id, String kind, String sourceKind, String sourceId, String body,
    String scheduledAt, String state, String createdAt, String conversationId, String snoozedUntil) {}
