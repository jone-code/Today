package com.today.agent;

public record ConversationMessageDto(
    String id, String conversationId, String role, String body, String status, String createdAt,
    String clientMessageId) {}
