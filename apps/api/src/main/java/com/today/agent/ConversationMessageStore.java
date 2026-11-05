package com.today.agent;

import com.today.common.EntityMapper;
import com.today.persistence.ConversationEntity;
import com.today.persistence.ConversationMessageEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConversationMessageStore {
  private final ConversationMapper mapper;
  private final ProposalService proposals;

  public ConversationMessageStore(ConversationMapper mapper, ProposalService proposals) {
    this.mapper = mapper;
    this.proposals = proposals;
  }

  @Transactional
  public Start begin(String conversationId, String userId, String text, String clientMessageId) {
    ConversationEntity conversation = mapper.lockById(conversationId, userId);
    if (conversation == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "conversation not found");
    ConversationMessageEntity prior = mapper.findMessageByClientId(userId, clientMessageId);
    if (prior != null) {
      if (!conversationId.equals(prior.getConversationId()) || !text.equals(prior.getBody())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "client message id already used");
      }
      if ("succeeded".equals(prior.getStatus())) {
        return replay(conversation, prior, userId);
      }
      throw new ResponseStatusException("processing".equals(prior.getStatus())
          ? HttpStatus.CONFLICT : HttpStatus.BAD_GATEWAY,
          "processing".equals(prior.getStatus()) ? "reply is still processing"
              : "previous reply failed; start a new attempt explicitly");
    }
    if (mapper.findProcessing(conversationId, userId) != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "another reply is still processing");
    }

    ConversationMessageEntity user = message(conversationId, userId, "user", text, clientMessageId);
    user.setStatus("processing");
    user.setGenerationAttempt(1);
    mapper.insertMessage(user);
    proposals.withdrawPendingForOptOut(userId, conversationId, text);
    return new Start(user, history(conversationId, userId, user), null);
  }

  @Transactional
  public Start retry(String conversationId, String userId, String messageId) {
    ConversationEntity conversation = mapper.lockById(conversationId, userId);
    if (conversation == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "conversation not found");
    ConversationMessageEntity user = mapper.findMessageById(messageId, userId, conversationId);
    if (user == null || !"user".equals(user.getRole())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "message not found");
    }
    if ("succeeded".equals(user.getStatus())) return replay(conversation, user, userId);
    if (!"failed".equals(user.getStatus()) || mapper.findProcessing(conversationId, userId) != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "reply is still processing");
    }
    if (mapper.retryFailedMessage(messageId, userId, EntityMapper.now()) != 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "message is no longer failed");
    }
    user.setStatus("processing");
    user.setGenerationAttempt(user.getGenerationAttempt() + 1);
    proposals.withdrawPendingForOptOut(userId, conversationId, user.getBody());
    return new Start(user, history(conversationId, userId, user), null);
  }

  private Start replay(ConversationEntity conversation, ConversationMessageEntity prior, String userId) {
    ConversationMessageEntity reply = mapper.findReply(userId, prior.getId());
    if (reply == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "reply is not available");
    return new Start(null, List.of(), new ConversationMessageResult(
        ConversationDtos.conversation(conversation, List.of(prior, reply)),
        ConversationDtos.message(prior), ConversationDtos.message(reply), "idempotent",
        proposals.bySourceMessage(prior.getId(), userId)));
  }

  private List<Map<String, String>> history(String conversationId, String userId, ConversationMessageEntity current) {
    List<ConversationMessageEntity> history = new ArrayList<>(
        mapper.listHistoryBefore(conversationId, userId, current.getCreatedAt(), 20));
    Collections.reverse(history);
    return history.stream()
        .filter(m -> !"failed".equals(m.getStatus()))
        .filter(m -> !ConversationSafetyPolicy.RESPONSE.equals(m.getBody())
            && !("user".equals(m.getRole())
                && ConversationSafetyPolicy.requiresEmergencyResponse(m.getBody())))
        .map(m -> Map.of("role", m.getRole(), "body", m.getBody()))
        .toList();
  }

  @Transactional
  public ConversationMessageResult finish(String conversationId, String userId, String clientMessageId, int attempt,
      String replyText, String provider, List<ConversationProposal> proposed) {
    ConversationEntity conversation = mapper.lockById(conversationId, userId);
    if (conversation == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "conversation not found");
    ConversationMessageEntity user = mapper.findMessageByClientId(userId, clientMessageId);
    if (user == null || !conversationId.equals(user.getConversationId()) || !"processing".equals(user.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "message is no longer processing");
    }
    if (mapper.updateMessageStatus(user.getId(), userId, "succeeded", attempt) != 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "message is no longer processing");
    }
    user.setStatus("succeeded");
    ConversationMessageEntity assistant = message(conversationId, userId, "assistant", replyText, null);
    assistant.setReplyToMessageId(user.getId());
    mapper.insertMessage(assistant);
    conversation.setUpdatedAt(EntityMapper.now());
    conversation.setVersion(conversation.getVersion() + 1);
    mapper.update(conversation);
    List<AgentProposalDto> candidates = proposals.createCandidates(conversationId, user.getId(), user.getBody(), proposed);
    return new ConversationMessageResult(
        ConversationDtos.conversation(conversation, List.of(user, assistant)),
        ConversationDtos.message(user), ConversationDtos.message(assistant), provider, candidates);
  }

  @Transactional
  public void fail(String userId, String messageId, int attempt) {
    mapper.updateMessageStatus(messageId, userId, "failed", attempt);
  }

  @Transactional
  public int failStale(Instant before) {
    return mapper.failStaleMessages(before);
  }

  private ConversationMessageEntity message(String conversationId, String userId,
      String role, String text, String clientMessageId) {
    ConversationMessageEntity message = new ConversationMessageEntity();
    message.setId(UUID.randomUUID().toString());
    message.setConversationId(conversationId);
    message.setUserId(userId);
    message.setRole(role);
    message.setBody(text);
    message.setClientMessageId(clientMessageId);
    message.setStatus("succeeded");
    message.setCreatedAt(EntityMapper.now());
    return message;
  }

  public record Start(ConversationMessageEntity userMessage,
      List<Map<String, String>> history, ConversationMessageResult replay) {}
}
