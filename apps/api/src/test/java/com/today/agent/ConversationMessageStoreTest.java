package com.today.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.today.persistence.ConversationEntity;
import com.today.persistence.ConversationMessageEntity;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ConversationMessageStoreTest {
  private final ConversationMapper mapper = mock(ConversationMapper.class);
  private final ProposalService proposals = mock(ProposalService.class);
  private final ConversationMessageStore store = new ConversationMessageStore(mapper, proposals);

  @Test
  void beginPersistsProcessingBeforeCallingModel() {
    when(mapper.lockById("c1", "u1")).thenReturn(conversation());
    when(mapper.listHistoryBefore(eq("c1"), eq("u1"), any(Instant.class), eq(20))).thenReturn(List.of());

    var start = store.begin("c1", "u1", "hello", "stable-id");

    assertEquals("processing", start.userMessage().getStatus());
    assertEquals(1, start.userMessage().getGenerationAttempt());
    assertEquals("stable-id", start.userMessage().getClientMessageId());
    verify(mapper).insertMessage(start.userMessage());
    verify(proposals).withdrawPendingForOptOut("u1", "c1", "hello");
  }

  @Test
  void optOutIsProcessedWhenTheUserMessageIsStoredEvenWithoutAModelReply() {
    when(mapper.lockById("c1", "u1")).thenReturn(conversation());
    when(mapper.listHistoryBefore(eq("c1"), eq("u1"), any(Instant.class), eq(20))).thenReturn(List.of());

    store.begin("c1", "u1", "不要记住这件事", "stable-id");

    verify(proposals).withdrawPendingForOptOut("u1", "c1", "不要记住这件事");
    verify(proposals, never()).createCandidates(any(), any(), any(), any());
  }

  @Test
  void duplicateProcessingIdCannotInsertSecondMessage() {
    when(mapper.lockById("c1", "u1")).thenReturn(conversation());
    ConversationMessageEntity previous = user("processing");
    when(mapper.findMessageByClientId("u1", "stable-id")).thenReturn(previous);

    assertThrows(ResponseStatusException.class, () -> store.begin("c1", "u1", "hello", "stable-id"));
    verify(mapper, never()).insertMessage(any());
  }

  @Test
  void duplicateFailedIdCannotRepeatModelCall() {
    when(mapper.lockById("c1", "u1")).thenReturn(conversation());
    when(mapper.findMessageByClientId("u1", "stable-id")).thenReturn(user("failed"));

    assertThrows(ResponseStatusException.class, () -> store.begin("c1", "u1", "hello", "stable-id"));
    verify(mapper, never()).insertMessage(any());
  }

  @Test
  void explicitRetryReusesFailedUserMessage() {
    when(mapper.lockById("c1", "u1")).thenReturn(conversation());
    when(mapper.findMessageById("message-1", "u1", "c1")).thenReturn(user("failed"));
    when(mapper.retryFailedMessage(eq("message-1"), eq("u1"), any(Instant.class))).thenReturn(1);
    when(mapper.listHistoryBefore(eq("c1"), eq("u1"), any(Instant.class), eq(20))).thenReturn(List.of());

    var start = store.retry("c1", "u1", "message-1");

    assertEquals("message-1", start.userMessage().getId());
    assertEquals("processing", start.userMessage().getStatus());
    assertEquals(2, start.userMessage().getGenerationAttempt());
    verify(mapper, never()).insertMessage(any());
  }

  @Test
  void retryHistoryIncludesOnlyMessagesBeforeTheFailedMessage() {
    when(mapper.lockById("c1", "u1")).thenReturn(conversation());
    ConversationMessageEntity current = user("failed");
    current.setCreatedAt(Instant.parse("2026-11-03T10:00:00Z"));
    ConversationMessageEntity previous = user("succeeded");
    previous.setId("previous");
    previous.setBody("yesterday");
    when(mapper.findMessageById("message-1", "u1", "c1")).thenReturn(current);
    when(mapper.retryFailedMessage(eq("message-1"), eq("u1"), any(Instant.class))).thenReturn(1);
    when(mapper.listHistoryBefore("c1", "u1", current.getCreatedAt(), 20)).thenReturn(List.of(previous));

    var start = store.retry("c1", "u1", "message-1");

    assertEquals(List.of(java.util.Map.of("role", "user", "body", "yesterday")), start.history());
    verify(mapper).listHistoryBefore("c1", "u1", current.getCreatedAt(), 20);
  }

  @Test
  void emergencyExchangeRemainsVisibleButIsNotSentAsOrdinaryModelHistory() {
    when(mapper.lockById("c1", "u1")).thenReturn(conversation());
    ConversationMessageEntity ordinary = user("succeeded");
    ordinary.setId("ordinary");
    ordinary.setBody("今天想聊我的旅行计划");
    ConversationMessageEntity crisis = user("succeeded");
    crisis.setId("crisis");
    crisis.setBody("我想伤害自己");
    ConversationMessageEntity overdose = user("succeeded");
    overdose.setId("overdose");
    overdose.setBody("我已经吃了很多安眠药");
    ConversationMessageEntity response = user("succeeded");
    response.setId("safety-response");
    response.setRole("assistant");
    response.setBody(ConversationSafetyPolicy.RESPONSE);
    when(mapper.listHistoryBefore(eq("c1"), eq("u1"), any(Instant.class), eq(20)))
        .thenReturn(List.of(response, overdose, crisis, ordinary));

    var start = store.begin("c1", "u1", "今天继续聊旅行", "new-id");

    assertEquals(List.of(java.util.Map.of("role", "user", "body", "今天想聊我的旅行计划")),
        start.history());
  }

  @Test
  void explicitRetryIsRejectedWhileAnotherGenerationIsRunning() {
    when(mapper.lockById("c1", "u1")).thenReturn(conversation());
    when(mapper.findMessageById("message-1", "u1", "c1")).thenReturn(user("failed"));
    when(mapper.findProcessing("c1", "u1")).thenReturn(user("processing"));

    assertThrows(ResponseStatusException.class, () -> store.retry("c1", "u1", "message-1"));

    verify(mapper, never()).retryFailedMessage(any(), any(), any());
  }

  @Test
  void finishRefusesToWriteAfterConversationDeletion() {
    assertThrows(ResponseStatusException.class,
        () -> store.finish("c1", "u1", "stable-id", 1, "reply", "llm", List.of()));
    verify(mapper, never()).insertMessage(any());
  }

  @Test
  void finishWritesOnlyOnceForProcessingMessage() {
    when(mapper.lockById("c1", "u1")).thenReturn(conversation());
    when(mapper.findMessageByClientId("u1", "stable-id")).thenReturn(user("processing"));
    when(mapper.updateMessageStatus("message-1", "u1", "succeeded", 1)).thenReturn(1);
    when(proposals.createCandidates(eq("c1"), eq("message-1"), eq("hello"), eq(List.of()))).thenReturn(List.of());

    var result = store.finish("c1", "u1", "stable-id", 1, "reply", "llm", List.of());

    assertEquals("succeeded", result.userMessage().status());
    assertEquals("reply", result.assistantMessage().body());
    verify(mapper).insertMessage(any(ConversationMessageEntity.class));
    verify(mapper).updateMessageStatus("message-1", "u1", "succeeded", 1);
  }

  @Test
  void finishPassesModelCandidatesToConfirmationFlow() {
    when(mapper.lockById("c1", "u1")).thenReturn(conversation());
    when(mapper.findMessageByClientId("u1", "stable-id")).thenReturn(user("processing"));
    when(mapper.updateMessageStatus("message-1", "u1", "succeeded", 1)).thenReturn(1);
    ConversationProposal candidate = new ConversationProposal("memory", "goal", "学日语", null, null);
    var card = new AgentProposalDto("p1", "memory", "{}", "pending", "message-1", Instant.now().toString());
    when(proposals.createCandidates("c1", "message-1", "hello", List.of(candidate))).thenReturn(List.of(card));

    var result = store.finish("c1", "u1", "stable-id", 1, "reply", "llm", List.of(candidate));

    assertEquals(List.of(card), result.proposals());
    verify(proposals).createCandidates("c1", "message-1", "hello", List.of(candidate));
  }

  @Test
  void oldAttemptCannotFinishOrFailRetriedGeneration() {
    when(mapper.lockById("c1", "u1")).thenReturn(conversation());
    ConversationMessageEntity retried = user("processing");
    retried.setGenerationAttempt(2);
    when(mapper.findMessageByClientId("u1", "stable-id")).thenReturn(retried);

    assertThrows(ResponseStatusException.class,
        () -> store.finish("c1", "u1", "stable-id", 1, "stale reply", "llm", List.of()));
    store.fail("u1", "message-1", 1);

    verify(mapper, never()).insertMessage(any());
    verify(mapper).updateMessageStatus("message-1", "u1", "succeeded", 1);
    verify(mapper).updateMessageStatus("message-1", "u1", "failed", 1);
    verify(mapper, never()).updateMessageStatus("message-1", "u1", "failed", 2);
  }

  private static ConversationEntity conversation() {
    ConversationEntity e = new ConversationEntity();
    e.setId("c1"); e.setUserId("u1"); e.setTitle("chat"); e.setStatus("active");
    e.setVersion(1); e.setCreatedAt(Instant.now()); e.setUpdatedAt(Instant.now());
    return e;
  }

  private static ConversationMessageEntity user(String status) {
    ConversationMessageEntity e = new ConversationMessageEntity();
    e.setId("message-1"); e.setConversationId("c1"); e.setUserId("u1");
    e.setClientMessageId("stable-id"); e.setRole("user"); e.setBody("hello");
    e.setStatus(status); e.setCreatedAt(Instant.now());
    e.setGenerationAttempt(1);
    return e;
  }
}
