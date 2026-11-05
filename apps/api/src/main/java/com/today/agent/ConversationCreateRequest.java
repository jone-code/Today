package com.today.agent;

import jakarta.validation.constraints.Size;

public record ConversationCreateRequest(@Size(max = 128) String title,
    @Size(max = 128) String clientRequestId) {}
