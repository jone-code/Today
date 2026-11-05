package com.today.agent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.today.aigateway.AiGatewayService;
import com.today.checkin.CheckinDto;
import com.today.checkin.CheckinService;
import com.today.common.Mood;
import com.today.common.RateLimitService;
import com.today.identity.IdentityService;
import com.today.memory.MemoryService;
import com.today.persistence.ConversationEntity;
import com.today.persistence.ConversationMessageEntity;
import com.today.todo.TodoService;
import com.today.timeline.TimelineItemDto;
import com.today.timeline.TimelinePageDto;
import com.today.timeline.TimelineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ConversationServiceTest {

  @Test
  void createReplaysTheSameConversationForTheSameUserAndRequestId() {
    ConversationMapper mapper = mock(ConversationMapper.class);
    IdentityService identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn("user-1");
    AtomicReference<ConversationEntity> persisted = new AtomicReference<>();
    when(mapper.insertIdempotent(any())).thenAnswer(invocation -> {
      persisted.compareAndSet(null, invocation.getArgument(0));
      return 1;
    });
    when(mapper.findByClientRequestId("user-1", "create-1")).thenAnswer(invocation -> persisted.get());
    RateLimitService limits = mock(RateLimitService.class);
    var service = service(mapper, identity, limits);

    var first = service.create(new ConversationCreateRequest(null, "create-1"));
    var replay = service.create(new ConversationCreateRequest(null, "create-1"));

    assertEquals(first.id(), replay.id());
    verify(mapper, times(2)).insertIdempotent(any());
    verify(mapper, times(2)).findByClientRequestId("user-1", "create-1");
    verify(limits).check("conversation_create_day", "user-1", 60, java.time.Duration.ofDays(1));
  }

  @Test
  void deletedConversationCannotBeRecreatedByRetry() {
    ConversationMapper mapper = mock(ConversationMapper.class);
    IdentityService identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn("user-1");
    ConversationEntity deleted = new ConversationEntity();
    deleted.setId("deleted-id"); deleted.setTitle("新的对话");
    deleted.setDeletedAt(Instant.now());
    when(mapper.findByClientRequestId("user-1", "create-1")).thenReturn(deleted);

    var failure = assertThrows(ResponseStatusException.class,
        () -> service(mapper, identity).create(new ConversationCreateRequest(null, "create-1")));

    assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
  }

  @Test
  void createLooksUpTheRequestOnlyWithinTheCurrentUser() {
    ConversationMapper mapper = mock(ConversationMapper.class);
    IdentityService identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn("user-2");
    ConversationEntity created = new ConversationEntity();
    created.setId("user-2-conversation"); created.setTitle("新的对话");
    created.setCreatedAt(Instant.now()); created.setUpdatedAt(Instant.now());
    when(mapper.findByClientRequestId("user-2", "create-1")).thenReturn(created);
    when(mapper.listMessages("user-2-conversation", "user-2", 1)).thenReturn(List.of());

    assertEquals("user-2-conversation", service(mapper, identity)
        .create(new ConversationCreateRequest(null, "create-1")).id());
    verify(mapper).findByClientRequestId("user-2", "create-1");
  }

  private static ConversationService service(ConversationMapper mapper, IdentityService identity) {
    return service(mapper, identity, mock(RateLimitService.class));
  }

  private static ConversationService service(ConversationMapper mapper, IdentityService identity,
      RateLimitService limits) {
    return new ConversationService(mapper, identity, mock(AiGatewayService.class),
        mock(ProposalService.class), mock(MemoryService.class), mock(FollowupMapper.class),
        mock(ConversationMessageStore.class), mock(AgentActionMapper.class),
        mock(AppointmentMapper.class), mock(AgentProfileService.class), mock(TodoService.class),
        mock(com.today.punch.PunchService.class), mock(com.today.reminder.ReminderService.class),
        mock(CheckinService.class), emptyTimeline(), limits);
  }

  private static TimelineService emptyTimeline() {
    TimelineService timeline = mock(TimelineService.class);
    when(timeline.list(6)).thenReturn(new TimelinePageDto(List.of(), null));
    return timeline;
  }

  @Test
  void messagePageReportsMoreAndRejectsIncompleteCursors() {
    ConversationMapper mapper = mock(ConversationMapper.class);
    IdentityService identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn("user-1");
    when(mapper.findById("conversation-1", "user-1")).thenReturn(new ConversationEntity());
    List<ConversationMessageEntity> rows = new java.util.ArrayList<>();
    for (int i = 0; i < 3; i++) {
      ConversationMessageEntity row = new ConversationMessageEntity();
      row.setId("message-" + i); row.setCreatedAt(Instant.now());
      rows.add(row);
    }
    when(mapper.listMessages("conversation-1", "user-1", 3)).thenReturn(rows);

    ConversationMessagePage page = service(mapper, identity).messages("conversation-1", null, null, 2);

    assertEquals(2, page.messages().size());
    assertEquals(true, page.hasMore());
    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
        () -> service(mapper, identity).messages("conversation-1", "2026-11-03T12:00:00Z", null, 2))
        .getStatusCode());
  }

  @Test
  void deleteInvalidatesSourcesAndMarksMessagesDeleted() {
    ConversationMapper mapper = mock(ConversationMapper.class);
    IdentityService identity = mock(IdentityService.class);
    ProposalService proposals = mock(ProposalService.class);
    MemoryService memories = mock(MemoryService.class);
    FollowupMapper followups = mock(FollowupMapper.class);
    AgentActionMapper actions = mock(AgentActionMapper.class);
    AppointmentMapper appointments = mock(AppointmentMapper.class);
    when(identity.getCurrentUserId()).thenReturn("user-1");
    when(mapper.lockById("conversation-1", "user-1")).thenReturn(new ConversationEntity());
    when(mapper.listMessageIds("conversation-1", "user-1")).thenReturn(List.of("message-1", "message-2"));
    when(mapper.softDelete(eq("conversation-1"), eq("user-1"), any(Instant.class))).thenReturn(1);

    new ConversationService(mapper, identity, mock(AiGatewayService.class), proposals, memories, followups,
        mock(ConversationMessageStore.class), actions, appointments, mock(AgentProfileService.class), mock(TodoService.class), mock(com.today.punch.PunchService.class), mock(com.today.reminder.ReminderService.class), mock(CheckinService.class), emptyTimeline(), mock(RateLimitService.class))
        .delete("conversation-1");

    for (String messageId : List.of("message-1", "message-2")) {
      verify(proposals).rejectPendingByMessage(messageId, "user-1");
      verify(memories).deactivateConversationSource("user-1", messageId);
    }
    verify(followups).pauseByConversation(eq("user-1"), eq("conversation-1"), any(Instant.class));
    verify(followups).redactInboxByConversation("user-1", "conversation-1");
    verify(actions).redactByConversation("user-1", "conversation-1");
    verify(appointments).cancelByConversation("user-1", "conversation-1");
    verify(appointments).redactInboxByConversation("user-1", "conversation-1");
    verify(mapper).softDeleteMessages(eq("conversation-1"), eq("user-1"), any(Instant.class));
    verify(mapper).softDelete(eq("conversation-1"), eq("user-1"), any(Instant.class));
  }

  @Test
  void replayNeverCallsModelAgain() {
    ConversationMapper mapper = mock(ConversationMapper.class);
    IdentityService identity = mock(IdentityService.class);
    AiGatewayService ai = mock(AiGatewayService.class);
    RateLimitService rateLimits = mock(RateLimitService.class);
    ConversationMessageStore store = mock(ConversationMessageStore.class);
    when(identity.getCurrentUserId()).thenReturn("u1");
    ConversationMessageResult replay = mock(ConversationMessageResult.class);
    when(store.begin("c1", "u1", "你好", "stable-id"))
        .thenReturn(new ConversationMessageStore.Start(null, List.of(), replay));

    var result = new ConversationService(mapper, identity, ai, mock(ProposalService.class),
        mock(MemoryService.class), mock(FollowupMapper.class), store, mock(AgentActionMapper.class),
        mock(AppointmentMapper.class), mock(AgentProfileService.class), mock(TodoService.class), mock(com.today.punch.PunchService.class), mock(com.today.reminder.ReminderService.class), mock(CheckinService.class), emptyTimeline(), rateLimits)
        .send("c1", new ConversationMessageRequest("你好", "stable-id"));

    assertSame(replay, result);
    verifyNoInteractions(ai);
    verifyNoInteractions(rateLimits);
  }

  @Test
  void modelFailureMarksPersistedMessageFailed() {
    IdentityService identity = mock(IdentityService.class);
    AiGatewayService ai = mock(AiGatewayService.class);
    ConversationMessageStore store = mock(ConversationMessageStore.class);
    AgentProfileService profiles = mock(AgentProfileService.class);
    ConversationMessageEntity user = new ConversationMessageEntity();
    user.setId("message-1");
    user.setBody("你好");
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(store.begin("c1", "u1", "你好", "stable-id"))
        .thenReturn(new ConversationMessageStore.Start(user, List.of(), null));
    when(profiles.getOrCreate()).thenReturn(new AgentProfileDto("u1", "小圆", "简短回应", null, null, 2));
    when(ai.completeRequired(eq(AiGatewayService.AiTask.conversation), any(), any()))
        .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThrows(ResponseStatusException.class,
        () -> new ConversationService(mock(ConversationMapper.class), identity, ai,
            mock(ProposalService.class), mock(MemoryService.class), mock(FollowupMapper.class), store,
            mock(AgentActionMapper.class), mock(AppointmentMapper.class), profiles, mock(TodoService.class), mock(com.today.punch.PunchService.class), mock(com.today.reminder.ReminderService.class), mock(CheckinService.class), emptyTimeline(), mock(RateLimitService.class))
            .send("c1", new ConversationMessageRequest("你好", "stable-id")));

    verify(store).fail("u1", "message-1", 0);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> prompt = ArgumentCaptor.forClass(Map.class);
    verify(ai).completeRequired(eq(AiGatewayService.AiTask.conversation), prompt.capture(), any());
    assertEquals("小圆", prompt.getValue().get("agentName"));
    assertEquals("简短回应", prompt.getValue().get("agentTone"));
    assertFalse(prompt.getValue().containsKey("avatarMediaId"));
    assertEquals(Map.of(), prompt.getValue().get("todayRecord"));
    assertEquals(List.of(), prompt.getValue().get("recentTimeline"));
  }

  @Test
  void modelReceivesTodayRecordAndBoundedEarlierTimeline() {
    IdentityService identity = mock(IdentityService.class);
    AiGatewayService ai = mock(AiGatewayService.class);
    ConversationMessageStore store = mock(ConversationMessageStore.class);
    AgentProfileService profiles = mock(AgentProfileService.class);
    CheckinService checkins = mock(CheckinService.class);
    TimelineService timeline = mock(TimelineService.class);
    ConversationMessageEntity user = new ConversationMessageEntity();
    user.setId("m1"); user.setBody("上周做了什么？");
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(store.begin("c1", "u1", user.getBody(), "key"))
        .thenReturn(new ConversationMessageStore.Start(user, List.of(), null));
    when(profiles.getOrCreate()).thenReturn(new AgentProfileDto("u1", "小圆", "简短回应", null, null, 2));
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    when(checkins.getByDate(today)).thenReturn(new CheckinDto("today-id", "u1", today.toString(),
        "上午做了复盘", "", ""));
    String longSummary = "回顾".repeat(100);
    when(timeline.list(6)).thenReturn(new TimelinePageDto(List.of(
        new TimelineItemDto(today.toString(), "今天", "不要重复", Mood.okay, "平常", "today-id"),
        new TimelineItemDto(today.minusDays(1).toString(), "昨天", longSummary, Mood.okay, "平常", "past-id")), null));
    when(ai.completeRequired(eq(AiGatewayService.AiTask.conversation), any(), any()))
        .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThrows(ResponseStatusException.class, () -> new ConversationService(mock(ConversationMapper.class),
        identity, ai, mock(ProposalService.class), mock(MemoryService.class), mock(FollowupMapper.class),
        store, mock(AgentActionMapper.class), mock(AppointmentMapper.class), profiles, mock(TodoService.class),
        mock(com.today.punch.PunchService.class), mock(com.today.reminder.ReminderService.class),
        checkins, timeline, mock(RateLimitService.class))
        .send("c1", new ConversationMessageRequest(user.getBody(), "key")));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> prompt = ArgumentCaptor.forClass(Map.class);
    verify(ai).completeRequired(eq(AiGatewayService.AiTask.conversation), prompt.capture(), any());
    assertEquals(Map.of("date", today.toString(), "text", "上午做了复盘"),
        prompt.getValue().get("todayRecord"));
    @SuppressWarnings("unchecked")
    List<Object> entries = (List<Object>) prompt.getValue().get("recentTimeline");
    assertEquals(1, entries.size());
    var entry = new ObjectMapper().valueToTree(entries.get(0));
    assertEquals(today.minusDays(1).toString(), entry.get("date").asText());
    assertEquals("昨天", entry.get("highlight").asText());
    assertEquals(longSummary.substring(0, 160), entry.get("oneLiner").asText());
    verify(timeline).list(6);
  }

  @Test
  void crisisReplyBypassesModelAndNeverCreatesProposals() {
    IdentityService identity = mock(IdentityService.class);
    AiGatewayService ai = mock(AiGatewayService.class);
    ConversationMessageStore store = mock(ConversationMessageStore.class);
    ConversationMessageEntity user = new ConversationMessageEntity();
    user.setId("message-1"); user.setClientMessageId("stable-id");
    user.setGenerationAttempt(1); user.setBody("我想伤害自己");
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(store.begin("c1", "u1", "我想伤害自己", "stable-id"))
        .thenReturn(new ConversationMessageStore.Start(user, List.of(), null));
    ConversationMessageResult safeResult = mock(ConversationMessageResult.class);
    when(store.finish("c1", "u1", "stable-id", 1, ConversationSafetyPolicy.RESPONSE, "safety", List.of()))
        .thenReturn(safeResult);

    var result = new ConversationService(mock(ConversationMapper.class), identity, ai,
        mock(ProposalService.class), mock(MemoryService.class), mock(FollowupMapper.class), store,
        mock(AgentActionMapper.class), mock(AppointmentMapper.class), mock(AgentProfileService.class), mock(TodoService.class), mock(com.today.punch.PunchService.class), mock(com.today.reminder.ReminderService.class), mock(CheckinService.class), emptyTimeline(), mock(RateLimitService.class))
        .send("c1", new ConversationMessageRequest("我想伤害自己", "stable-id"));

    assertSame(safeResult, result);
    verifyNoInteractions(ai);
    verify(store).finish("c1", "u1", "stable-id", 1, ConversationSafetyPolicy.RESPONSE, "safety", List.of());
  }

  @Test
  void explicitOverdoseBypassesModelAndProposalGeneration() {
    IdentityService identity = mock(IdentityService.class);
    AiGatewayService ai = mock(AiGatewayService.class);
    ConversationMessageStore store = mock(ConversationMessageStore.class);
    ConversationMessageEntity user = new ConversationMessageEntity();
    user.setId("overdose-message"); user.setClientMessageId("overdose-id");
    user.setGenerationAttempt(1); user.setBody("我已经吃了很多安眠药");
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(store.begin("c1", "u1", user.getBody(), "overdose-id"))
        .thenReturn(new ConversationMessageStore.Start(user, List.of(), null));

    new ConversationService(mock(ConversationMapper.class), identity, ai,
        mock(ProposalService.class), mock(MemoryService.class), mock(FollowupMapper.class), store,
        mock(AgentActionMapper.class), mock(AppointmentMapper.class), mock(AgentProfileService.class),
        mock(TodoService.class), mock(com.today.punch.PunchService.class),
        mock(com.today.reminder.ReminderService.class), mock(CheckinService.class), emptyTimeline(), mock(RateLimitService.class))
        .send("c1", new ConversationMessageRequest(user.getBody(), "overdose-id"));

    verifyNoInteractions(ai);
    verify(store).finish("c1", "u1", "overdose-id", 1,
        ConversationSafetyPolicy.RESPONSE, "safety", List.of());
  }
}
