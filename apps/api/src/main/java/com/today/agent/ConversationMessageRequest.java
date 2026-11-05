package com.today.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConversationMessageRequest(
    @NotBlank @Size(max = 4000) String body,
    @NotBlank @Size(max = 128) String clientMessageId) {}
