package com.today.agent;

import java.util.List;

public record ConversationMessagePage(List<ConversationMessageDto> messages, boolean hasMore) {}
