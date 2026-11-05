package com.today.agent;

import com.today.persistence.ConversationEntity;
import com.today.persistence.ConversationMessageEntity;
import java.util.List;

final class ConversationDtos {
  private ConversationDtos() {}

  static ConversationDto conversation(ConversationEntity entity, List<ConversationMessageEntity> messages) {
    return new ConversationDto(entity.getId(), entity.getTitle(), entity.getStatus(), entity.getVersion(),
        entity.getCreatedAt().toString(), entity.getUpdatedAt().toString(),
        messages.stream().map(ConversationDtos::message).toList());
  }

  static ConversationMessageDto message(ConversationMessageEntity entity) {
    return new ConversationMessageDto(entity.getId(), entity.getConversationId(), entity.getRole(),
        entity.getBody(), entity.getStatus(), entity.getCreatedAt().toString(), entity.getClientMessageId());
  }
}
