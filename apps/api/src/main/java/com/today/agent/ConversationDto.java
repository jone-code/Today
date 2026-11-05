package com.today.agent;

import java.util.List;

public record ConversationDto(
    String id,
    String title,
    String status,
    int version,
    String createdAt,
    String updatedAt,
    List<ConversationMessageDto> messages) {}
