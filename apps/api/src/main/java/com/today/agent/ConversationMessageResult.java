package com.today.agent;

import java.util.List;

public record ConversationMessageResult(
    ConversationDto conversation,
    ConversationMessageDto userMessage,
    ConversationMessageDto assistantMessage,
    String provider,
    List<AgentProposalDto> proposals) {}
