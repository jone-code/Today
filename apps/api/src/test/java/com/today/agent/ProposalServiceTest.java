package com.today.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.today.identity.IdentityService;
import com.today.memory.MemoryService;
import com.today.memory.MemoryDto;
import com.today.common.MemoryCategory;
import com.today.checkin.CheckinAiJobService;
import com.today.checkin.CheckinDto;
import com.today.checkin.CheckinService;
import com.today.persistence.AgentProposalEntity;
import com.today.todo.TodoCreateRequest;
import com.today.todo.TodoDto;
import com.today.todo.TodoService;
import com.today.todo.TodoUpdateRequest;
import com.today.punch.PunchService;
import com.today.punch.PunchHabitDto;
import com.today.punch.PunchService.PunchAgentSnapshot;
import com.today.reminder.ReminderDto;
import com.today.reminder.ReminderService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

class ProposalServiceTest {
  private final AgentProposalMapper mapper = mock(AgentProposalMapper.class);
  private final IdentityService identity = mock(IdentityService.class);
  private final FollowupService followups = mock(FollowupService.class);
  private final CheckinService checkins = mock(CheckinService.class);
  private final CheckinAiJobService checkinJobs = mock(CheckinAiJobService.class);
  private final ProposalService service = new ProposalService(mapper, identity,
      mock(MemoryService.class), new ObjectMapper(), followups,
      mock(com.today.todo.TodoService.class), mock(com.today.punch.PunchService.class),
      mock(com.today.reminder.ReminderService.class), mock(AgentActionMapper.class),
      mock(AppointmentService.class), checkins, checkinJobs);

  @Test
  void noModelCandidatesNeverCreatesProposals() {
    assertTrue(service.createCandidates("c1", "m1", "hello", null).isEmpty());
    assertTrue(service.createCandidates("c1", "m1", "hello", List.of()).isEmpty());
    verify(mapper, never()).insert(any());
  }

  @Test
  void explicitOptOutSuppressesUntrustedModelSuggestionsAndWithdrawsPendingCards() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    var created = service.createCandidates("c1", "m1", "请 不要记住这件事，也别再问我了", List.of(
        new ConversationProposal("memory", "goal", "不想保存的内容", null, null),
        new ConversationProposal("followup", null, null, "不想再问的内容", 7)));

    assertTrue(created.isEmpty());
    service.withdrawPendingForOptOut("u1", "c1", "请 不要记住这件事，也别再问我了");
    verify(mapper).rejectPendingKindByConversation(eq("u1"), eq("c1"), eq("memory"), any());
    verify(mapper).rejectPendingKindByConversation(eq("u1"), eq("c1"), eq("followup"), any());
    verify(mapper, never()).insert(any());
  }

  @Test
  void denialStillWithdrawsPendingCardsWhenModelReturnsNoProposals() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    assertTrue(service.createCandidates("c1", "m1", "别记住了", null).isEmpty());
    service.withdrawPendingForOptOut("u1", "c1", "别记住了");
    verify(mapper).rejectPendingKindByConversation(eq("u1"), eq("c1"), eq("memory"), any());
    verify(mapper, never()).insert(any());
  }

  @Test
  void onlyValidatedCandidatesBecomeConfirmationCards() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    var created = service.createCandidates("c1", "m1", "hello", List.of(
        new ConversationProposal("memory", "invalid", "a", null, null),
        new ConversationProposal("memory", "goal", "  我想学日语  ", null, null),
        new ConversationProposal("memory", "goal", "duplicate", null, null),
        new ConversationProposal("followup", null, null, "  日语学习进展  ", 7),
        new ConversationProposal("todo", null, "write", null, null)));

    assertEquals(2, created.size());
    ArgumentCaptor<AgentProposalEntity> inserted = ArgumentCaptor.forClass(AgentProposalEntity.class);
    verify(mapper, org.mockito.Mockito.times(2)).insert(inserted.capture());
    assertEquals("memory", inserted.getAllValues().get(0).getKind());
    assertTrue(inserted.getAllValues().get(0).getPayloadJson().contains("\"category\":\"goal\""));
    assertTrue(inserted.getAllValues().get(0).getPayloadJson().contains("\"text\":\"我想学日语\""));
    assertEquals("followup", inserted.getAllValues().get(1).getKind());
    assertTrue(inserted.getAllValues().get(1).getPayloadJson().contains("日语学习进展"));
  }

  @Test
  void outOfRangeAndBlankSuggestionsAreIgnored() {
    assertTrue(service.createCandidates("c1", "m1", "hello", List.of(
        new ConversationProposal("followup", null, null, "soon", 0),
        new ConversationProposal("followup", null, null, " ", 7),
        new ConversationProposal("memory", "life", " ", null, null))).isEmpty());
    verify(mapper, never()).insert(any());
  }

  @Test
  void rejectedFollowupReasonIsNotSuggestedAgainInTheSameConversation() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(mapper.hasRejectedFollowupReason("u1", "c1", "日语学习进展")).thenReturn(true);
    assertTrue(service.createCandidates("c1", "m1", "最近还在学日语", List.of(
        new ConversationProposal("followup", null, null, "日语学习进展", 7))).isEmpty());
    verify(mapper, never()).insert(any());
  }

  @Test
  void internalOperationsRequireValidDatesAndTimes() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(followups.preferences()).thenReturn(new FollowupPreferenceDto(true, 1, 3, "Asia/Shanghai"));
    var created = service.createCandidates("c1", "m1", "请每天 09:00 提醒我喝水，帮我把准备材料记成待办，再创建跑步习惯", List.of(
        new ConversationProposal("todo", null, null, null, null, "准备材料", null, "2026-02-30", null, null),
        new ConversationProposal("todo", null, null, null, null, "准备材料", "带文件", "2026-11-04", null, null),
        new ConversationProposal("habit", null, null, null, null, "跑步", null, null, "每周运动", null),
        new ConversationProposal("daily_reminder", null, null, null, null, "喝水", null, null, null, "25:00"),
        new ConversationProposal("daily_reminder", null, null, null, null, "喝水", null, null, null, "09:00")));

    assertEquals(List.of("todo", "habit", "daily_reminder"), created.stream().map(AgentProposalDto::kind).toList());
    ArgumentCaptor<AgentProposalEntity> inserted = ArgumentCaptor.forClass(AgentProposalEntity.class);
    verify(mapper, Mockito.times(3)).insert(inserted.capture());
    assertTrue(inserted.getAllValues().get(0).getPayloadJson().contains("2026-11-04"));
    assertTrue(inserted.getAllValues().get(2).getPayloadJson().contains("Asia/Shanghai"));
  }

  @Test
  void casualConversationCannotBecomeAnUnrequestedInternalOperation() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    var modelSuggestions = List.of(
        new ConversationProposal("todo", null, null, null, null, "整理旅行清单", null, null, null, null),
        new ConversationProposal("habit", null, null, null, null, "每天跑步", null, null, null, null),
        new ConversationProposal("daily_reminder", null, null, null, null, "喝水", null, null, null, "09:00"));

    assertTrue(service.createCandidates("c1", "m1", "每天早上九点喝水，最近在练习跑步，想聊聊旅行清单", modelSuggestions).isEmpty());
    verify(mapper, never()).insert(any());
    verifyNoInteractions(followups);
  }

  @Test
  void creatingAnOperationRequiresItsOwnIntentEvenWhenOtherActionsWereRequested() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    var created = service.createCandidates("c1", "m1", "帮我把整理旅行清单加到待办", List.of(
        new ConversationProposal("todo", null, null, null, null, "整理旅行清单", null, null, null, null),
        new ConversationProposal("habit", null, null, null, null, "跑步", null, null, null, null)));

    assertEquals(List.of("todo"), created.stream().map(AgentProposalDto::kind).toList());
  }

  @Test
  void commonExplicitRequestsStillProduceActionCards() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(followups.preferences()).thenReturn(new FollowupPreferenceDto(true, 1, 3, "Asia/Shanghai"));
    assertEquals(List.of("todo", "habit", "daily_reminder"), service.createCandidates(
        "c1", "m1", "加一个买菜待办，帮我养成早睡习惯，再帮我建个每天喝水提醒", List.of(
            new ConversationProposal("todo", null, null, null, null, "买菜", null, null, null, null),
            new ConversationProposal("habit", null, null, null, null, "早睡", null, null, null, null),
            new ConversationProposal("daily_reminder", null, null, null, null, "喝水", null, null, null, "09:00")))
        .stream().map(AgentProposalDto::kind).toList());
  }

  @Test
  void negatedCreationDoesNotProduceActionCards() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    var todo = new ConversationProposal("todo", null, null, null, null, "旅行清单", null, null, null, null);
    var habit = new ConversationProposal("habit", null, null, null, null, "跑步", null, null, null, null);
    var reminder = new ConversationProposal("daily_reminder", null, null, null, null,
        "喝水", null, null, null, "09:00");
    assertTrue(service.createCandidates("c1", "m1", "不要添加旅行清单到待办", List.of(todo)).isEmpty());
    assertTrue(service.createCandidates("c1", "m1", "先别创建跑步习惯", List.of(habit)).isEmpty());
    assertTrue(service.createCandidates("c1", "m1", "不用每天提醒我喝水", List.of(reminder)).isEmpty());
    verify(mapper, never()).insert(any());
    verifyNoInteractions(followups);
  }

  @Test
  void negatingOneOperationDoesNotPreventAnotherRequestedOperation() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    var cards = service.createCandidates("c1", "m1", "不要添加待办，帮我创建跑步习惯", List.of(
        new ConversationProposal("todo", null, null, null, null, "跑步", null, null, null, null),
        new ConversationProposal("habit", null, null, null, null, "跑步", null, null, null, null)));
    assertEquals(List.of("habit"), cards.stream().map(AgentProposalDto::kind).toList());
  }

  @Test
  void oneTimeRequestCannotBecomeDailyReminder() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    assertTrue(service.createCandidates("c1", "m1", "明天早上九点提醒我喝水", List.of(
        new ConversationProposal("daily_reminder", null, null, null, null,
            "喝水", null, null, null, "09:00"))).isEmpty());
    verifyNoInteractions(followups);
    verify(mapper, never()).insert(any());
  }

  @Test
  void punchProposalRequiresVisibleMatchingHabitAndExplicitDate() {
    PunchService punches = mock(PunchService.class);
    ProposalService proposing = new ProposalService(mapper, identity, mock(MemoryService.class),
        new ObjectMapper(), followups, mock(TodoService.class), punches,
        mock(com.today.reminder.ReminderService.class), mock(AgentActionMapper.class),
        mock(AppointmentService.class), checkins, checkinJobs);
    when(identity.getCurrentUserId()).thenReturn("u1");
    String date = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
    PunchHabitDto habit = new PunchHabitDto("h1", "u1", "跑步", null, true, "", "", false, 0, null);
    when(punches.listForAgent(20)).thenReturn(List.of(habit));
    when(followups.preferences()).thenReturn(new FollowupPreferenceDto(true, 1, 3, "Asia/Shanghai"));
    when(punches.snapshotForAgent("h1", LocalDate.parse(date)))
        .thenReturn(new PunchAgentSnapshot("h1", "跑步", "2026-11-03T09:00:00Z", true, date, null, null, false));
    var good = new ConversationProposal("habit_punch", null, null, null, null,
        null, null, null, null, null, null, null, "h1", null, date);
    var wrong = new ConversationProposal("habit_punch", null, null, null, null,
        null, null, null, null, null, null, null, "someone-else", null, date);
    var missingDate = new ConversationProposal("habit_punch", null, null, null, null,
        null, null, null, null, null, null, null, "h1", null, null);
    assertTrue(proposing.createCandidates("c1", "m1", "今天帮我打卡跑步", List.of(wrong, missingDate)).isEmpty());
    assertTrue(proposing.createCandidates("c1", "m1", "今天帮我打卡阅读", List.of(good)).isEmpty());
    var created = proposing.createCandidates("c1", "m1", "今天帮我打卡跑步", List.of(good));
    assertEquals(1, created.size());
    assertTrue(created.get(0).payloadJson().contains("\"date\":\"" + date + "\""));
    verify(mapper).insert(any());
  }

  @Test
  void cancelPunchProposalWarnsAboutExistingPhotoAndRequiresExplicitCancel() {
    PunchService punches = mock(PunchService.class);
    ProposalService proposing = new ProposalService(mapper, identity, mock(MemoryService.class),
        new ObjectMapper(), followups, mock(TodoService.class), punches,
        mock(com.today.reminder.ReminderService.class), mock(AgentActionMapper.class),
        mock(AppointmentService.class), checkins, checkinJobs);
    when(identity.getCurrentUserId()).thenReturn("u1");
    String date = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
    when(followups.preferences()).thenReturn(new FollowupPreferenceDto(true, 1, 3, "Asia/Shanghai"));
    when(punches.listForAgent(20)).thenReturn(List.of(
        new PunchHabitDto("h1", "u1", "跑步", null, true, "", "", false, 0, null)));
    when(punches.snapshotForAgent("h1", LocalDate.parse(date)))
        .thenReturn(new PunchAgentSnapshot("h1", "跑步", "2026-11-03T09:00:00Z", true, date, "log-1", "fingerprint", true));
    var suggestion = new ConversationProposal("habit_unpunch", null, null, null, null,
        null, null, null, null, null, null, null, "h1", null, date);
    assertTrue(proposing.createCandidates("c1", "m1", "我打卡跑步了", List.of(suggestion)).isEmpty());
    var created = proposing.createCandidates("c1", "m1", "请取消打卡跑步", List.of(suggestion));
    assertEquals(1, created.size());
    assertTrue(created.get(0).payloadJson().contains("\"hasPhoto\":true"));
  }

  @Test
  void habitEditRequiresVisibleMatchingHabitAndOnlyRequestedFields() {
    PunchService punches = mock(PunchService.class);
    ProposalService proposing = new ProposalService(mapper, identity, mock(MemoryService.class),
        new ObjectMapper(), followups, mock(TodoService.class), punches,
        mock(com.today.reminder.ReminderService.class), mock(AgentActionMapper.class),
        mock(AppointmentService.class), checkins, checkinJobs);
    when(identity.getCurrentUserId()).thenReturn("u1");
    var habit = new PunchHabitDto("h1", "u1", "跑步", "每周运动", true,
        "2026-11-03T09:00:00Z", "2026-11-03T09:00:00Z", false, 0, null);
    var changed = new ConversationProposal("habit_edit", null, null, null, null,
        "晨跑", null, null, null, null, null, null, "h1", null, null, null);
    assertTrue(proposing.createCandidates("c1", "m1", "把跑步改成晨跑", List.of(changed)).isEmpty());
    when(punches.listForAgent(20)).thenReturn(List.of(habit));
    assertTrue(proposing.createCandidates("c1", "m1", "把阅读改成晨跑", List.of(changed)).isEmpty());
    assertTrue(proposing.createCandidates("c1", "m1", "把跑步改成夜跑", List.of(changed)).isEmpty());
    var unchanged = new ConversationProposal("habit_edit", null, null, null, null,
        "跑步", null, null, null, null, null, null, "h1", null, null, null);
    assertTrue(proposing.createCandidates("c1", "m1", "修改跑步", List.of(unchanged)).isEmpty());
    var cards = proposing.createCandidates("c1", "m1", "把跑步改成晨跑", List.of(changed));
    assertEquals(1, cards.size());
    assertTrue(cards.get(0).payloadJson().contains("\"targetId\":\"h1\""));
    assertTrue(cards.get(0).payloadJson().contains("\"title\":\"晨跑\""));
    assertTrue(cards.get(0).payloadJson().contains("\"description\":\"每周运动\""));
  }

  @Test
  void habitStatusChangeRequiresMatchingPauseOrResumeIntent() {
    PunchService punches = mock(PunchService.class);
    ProposalService proposing = new ProposalService(mapper, identity, mock(MemoryService.class),
        new ObjectMapper(), followups, mock(TodoService.class), punches,
        mock(com.today.reminder.ReminderService.class), mock(AgentActionMapper.class),
        mock(AppointmentService.class), checkins, checkinJobs);
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(punches.listForAgent(20)).thenReturn(List.of(new PunchHabitDto("h1", "u1", "跑步", null, true,
        "2026-11-03T09:00:00Z", "2026-11-03T09:00:00Z", false, 0, null)));
    var pause = new ConversationProposal("habit_edit", null, null, null, null,
        null, null, null, null, null, null, null, "h1", null, null, false);
    assertTrue(proposing.createCandidates("c1", "m1", "修改跑步名字", List.of(pause)).isEmpty());
    var cards = proposing.createCandidates("c1", "m1", "暂停跑步", List.of(pause));
    assertEquals(1, cards.size());
    assertTrue(cards.get(0).payloadJson().contains("\"enabled\":false"));
  }

  @Test
  void reminderEditRequiresVisibleTargetExplicitTimeAndValidTimezone() {
    ReminderService reminders = mock(ReminderService.class);
    ProposalService proposing = new ProposalService(mapper, identity, mock(MemoryService.class),
        new ObjectMapper(), followups, mock(TodoService.class), mock(PunchService.class),
        reminders, mock(AgentActionMapper.class), mock(AppointmentService.class), checkins, checkinJobs);
    when(identity.getCurrentUserId()).thenReturn("u1");
    var reminder = new ReminderDto("r1", "u1", "喝水", "喝水休息", "09:00", "Asia/Shanghai",
        true, "2026-11-03T09:00:00Z", "2026-11-03T09:00:00Z");
    var changeTime = reminderEdit("r1", "10:30", null, null);
    assertTrue(proposing.createCandidates("c1", "m1", "把喝水提醒改到10:30", List.of(changeTime)).isEmpty());
    when(reminders.listForAgent(20)).thenReturn(List.of(reminder));
    assertTrue(proposing.createCandidates("c1", "m1", "把咖啡提醒改到10:30", List.of(changeTime)).isEmpty());
    assertTrue(proposing.createCandidates("c1", "m1", "修改喝水提醒", List.of(changeTime)).isEmpty());
    assertTrue(proposing.createCandidates("c1", "m1", "把喝水提醒改到10:30",
        List.of(reminderEdit("r1", "10:30", "Invalid/Zone", null))).isEmpty());
    assertTrue(proposing.createCandidates("c1", "m1", "把喝水提醒改到10:30",
        List.of(reminderEdit("r1", "25:30", null, null))).isEmpty());
    var cards = proposing.createCandidates("c1", "m1", "把喝水提醒改到10:30", List.of(changeTime));
    assertEquals(1, cards.size());
    assertTrue(cards.get(0).payloadJson().contains("\"remindTime\":\"10:30\""));
    assertTrue(cards.get(0).payloadJson().contains("\"message\":\"喝水休息\""));
  }

  private ConversationProposal reminderEdit(String id, String time, String timezone, Boolean enabled) {
    return new ConversationProposal("daily_reminder_edit", null, null, null, null,
        null, null, null, null, time, null, null, id, null, null, null,
        null, timezone, enabled);
  }

  @Test
  void oneOffProposalUsesUserTimezoneAndRejectsAmbiguousTimes() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(followups.preferences()).thenReturn(new FollowupPreferenceDto(true, 1, 3, "Asia/Shanghai"));
    String tomorrow = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1) + "T09:00";
    var created = service.createCandidates("c1", "m1", "明天早上九点提醒我带文件", List.of(
        new ConversationProposal("one_off_reminder", null, null, null, null,
            "带文件", null, null, null, null, tomorrow)));

    assertEquals(1, created.size());
    ArgumentCaptor<AgentProposalEntity> inserted = ArgumentCaptor.forClass(AgentProposalEntity.class);
    verify(mapper).insert(inserted.capture());
    assertTrue(inserted.getValue().getPayloadJson().contains("\"scheduledLocal\":\"" + tomorrow + "\""));
    assertTrue(inserted.getValue().getPayloadJson().contains("\"timezone\":\"Asia/Shanghai\""));
    assertTrue(inserted.getValue().getPayloadJson().contains("\"dueAtUtc\":\""
        + LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).atTime(9, 0)
            .atZone(ZoneId.of("Asia/Shanghai")).toInstant() + "\""));
    assertEquals(LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).atTime(9, 0)
        .atZone(ZoneId.of("Asia/Shanghai")).toInstant(), inserted.getValue().getExpiresAt());
  }

  @Test
  void oneOffRejectsMissingIntentAndDstGap() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(followups.preferences()).thenReturn(new FollowupPreferenceDto(true, 1, 3, "America/New_York"));
    var suggestion = new ConversationProposal("one_off_reminder", null, null, null, null,
        "带文件", null, null, null, null, "2027-03-14T02:30");

    assertTrue(service.createCandidates("c1", "m1", "明天带文件", List.of(suggestion)).isEmpty());
    assertTrue(service.createCandidates("c1", "m1", "到时提醒我带文件", List.of(suggestion)).isEmpty());
    verify(mapper, never()).insert(any());
  }

  @Test
  void oneOffRejectsNegatedReminderRequest() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    String tomorrow = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1) + "T09:00";
    var suggestion = new ConversationProposal("one_off_reminder", null, null, null, null,
        "带文件", null, null, null, null, tomorrow);
    assertTrue(service.createCandidates("c1", "m1", "明天九点不要提醒我带文件", List.of(suggestion)).isEmpty());
    assertTrue(service.createCandidates("c1", "m1", "明天九点先别提醒我带文件", List.of(suggestion)).isEmpty());
    verify(mapper, never()).insert(any());
    verifyNoInteractions(followups);
  }

  @Test
  void oneOffDoesNotInventAnUnspecifiedTime() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    String tomorrow = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1) + "T09:00";
    var suggestion = new ConversationProposal("one_off_reminder", null, null, null, null,
        "带文件", null, null, null, null, tomorrow);
    assertTrue(service.createCandidates("c1", "m1", "明天提醒我带文件", List.of(suggestion)).isEmpty());
    assertTrue(service.createCandidates("c1", "m1", "明天上午记得提醒我带文件", List.of(suggestion)).isEmpty());
    verify(mapper, never()).insert(any());
    verifyNoInteractions(followups);
  }

  @Test
  void oneOffAcceptsExplicitRelativeTime() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(followups.preferences()).thenReturn(new FollowupPreferenceDto(true, 1, 3, "Asia/Shanghai"));
    String due = java.time.LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
        .plusMinutes(30).withSecond(0).withNano(0).toString();
    var suggestion = new ConversationProposal("one_off_reminder", null, null, null, null,
        "带文件", null, null, null, null, due);
    assertEquals(1, service.createCandidates("c1", "m1", "半小时后提醒我带文件", List.of(suggestion)).size());
  }

  @Test
  void confirmedTodoUsesExistingServiceAndRecordsActualTarget() {
    TodoService todos = mock(TodoService.class);
    AgentActionMapper actions = mock(AgentActionMapper.class);
    ProposalService confirming = new ProposalService(mapper, identity, mock(MemoryService.class),
        new ObjectMapper(), followups, todos, mock(com.today.punch.PunchService.class),
        mock(com.today.reminder.ReminderService.class), actions, mock(AppointmentService.class), checkins, checkinJobs);
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(mapper.lockSourceConversation("p1", "u1")).thenReturn("c1");
    var pending = pending("todo", "{\"title\":\"准备材料\",\"note\":\"带文件\",\"dueDate\":\"2026-11-04\"}");
    var confirmed = pending("todo", pending.getPayloadJson());
    confirmed.setStatus("confirmed");
    when(mapper.findOwned("p1", "u1")).thenReturn(pending, confirmed);
    when(mapper.updateStatus(eq("p1"), eq("u1"), eq("confirmed"), any(Instant.class))).thenReturn(1);
    TodoDto todo = mock(TodoDto.class);
    when(todo.id()).thenReturn("todo-1");
    when(todos.create(new TodoCreateRequest("准备材料", "带文件", "2026-11-04"))).thenReturn(todo);
    when(actions.insert(eq("p1"), eq("u1"), eq("todo"), eq("todo-1"), eq("准备材料"), any(Instant.class)))
        .thenReturn(1);

    assertEquals("confirmed", confirming.confirm("p1").status());
    InOrder order = Mockito.inOrder(mapper, todos, actions);
    order.verify(mapper).updateStatus(eq("p1"), eq("u1"), eq("confirmed"), any(Instant.class));
    order.verify(todos).create(new TodoCreateRequest("准备材料", "带文件", "2026-11-04"));
    order.verify(actions).insert(eq("p1"), eq("u1"), eq("todo"), eq("todo-1"), eq("准备材料"), any(Instant.class));
  }

  @Test
  void confirmationClaimsProposalBeforeWritingMemory() {
    MemoryService memories = mock(MemoryService.class);
    AgentActionMapper actions = mock(AgentActionMapper.class);
    ProposalService confirming = new ProposalService(mapper, identity, memories,
        new ObjectMapper(), mock(FollowupService.class), mock(com.today.todo.TodoService.class),
        mock(com.today.punch.PunchService.class), mock(com.today.reminder.ReminderService.class), actions,
        mock(AppointmentService.class), checkins, checkinJobs);
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(mapper.lockSourceConversation("p1", "u1")).thenReturn("c1");
    AgentProposalEntity pending = pending("memory", "{\"category\":\"goal\",\"text\":\"学日语\"}");
    AgentProposalEntity confirmed = pending("memory", pending.getPayloadJson());
    confirmed.setStatus("confirmed");
    when(mapper.findOwned("p1", "u1")).thenReturn(pending, confirmed);
    when(mapper.updateStatus(eq("p1"), eq("u1"), eq("confirmed"), any(Instant.class))).thenReturn(1);
    MemoryDto memory = mock(MemoryDto.class);
    when(memory.id()).thenReturn("memory-1");
    when(memories.confirmFromProposal("u1", MemoryCategory.goal, "学日语", "conversation_message", "m1"))
        .thenReturn(memory);
    when(actions.insert(eq("p1"), eq("u1"), eq("memory"), eq("memory-1"), eq("学日语"), any(Instant.class))).thenReturn(1);

    assertEquals("confirmed", confirming.confirm("p1").status());

    InOrder order = Mockito.inOrder(mapper, memories, actions);
    order.verify(mapper).lockSourceConversation("p1", "u1");
    order.verify(mapper).updateStatus(eq("p1"), eq("u1"), eq("confirmed"), any(Instant.class));
    order.verify(memories).confirmFromProposal("u1", MemoryCategory.goal, "学日语", "conversation_message", "m1");
    order.verify(actions).insert(eq("p1"), eq("u1"), eq("memory"), eq("memory-1"), eq("学日语"), any(Instant.class));
  }

  @Test
  void lostConfirmationRaceDoesNotExecuteAction() {
    MemoryService memories = mock(MemoryService.class);
    FollowupService followups = mock(FollowupService.class);
    ProposalService confirming = new ProposalService(mapper, identity, memories,
        new ObjectMapper(), followups, mock(com.today.todo.TodoService.class),
        mock(com.today.punch.PunchService.class), mock(com.today.reminder.ReminderService.class),
        mock(AgentActionMapper.class), mock(AppointmentService.class), checkins, checkinJobs);
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(mapper.lockSourceConversation("p1", "u1")).thenReturn("c1");
    when(mapper.findOwned("p1", "u1")).thenReturn(pending("followup",
        "{\"reason\":\"继续学习\",\"suggestedDays\":7,\"conversationId\":\"c1\"}"));

    assertThrows(ResponseStatusException.class, () -> confirming.confirm("p1"));

    verify(mapper).updateStatus(eq("p1"), eq("u1"), eq("confirmed"), any(Instant.class));
    verifyNoInteractions(memories, followups);
  }

  @Test
  void deletedSourceCannotBeConfirmed() {
    MemoryService memories = mock(MemoryService.class);
    ProposalService confirming = new ProposalService(mapper, identity, memories,
        new ObjectMapper(), mock(FollowupService.class), mock(com.today.todo.TodoService.class),
        mock(com.today.punch.PunchService.class), mock(com.today.reminder.ReminderService.class),
        mock(AgentActionMapper.class), mock(AppointmentService.class), checkins, checkinJobs);
    when(identity.getCurrentUserId()).thenReturn("u1");

    assertThrows(ResponseStatusException.class, () -> confirming.confirm("p1"));

    verify(mapper, never()).findOwned(any(), any());
    verifyNoInteractions(memories);
  }

  @Test
  void checkinProposalPreviewsMergeWithoutWritingAndCanBeEdited() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(followups.preferences()).thenReturn(new FollowupPreferenceDto(true, 1, 3, "Asia/Shanghai"));
    LocalDate date = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    when(checkins.getByDate(date)).thenReturn(new CheckinDto("c1", "u1", date.toString(),
        "上午开会", "2026-11-03T00:00:00Z", "2026-11-03T01:00:00Z"));
    var suggestion = new ConversationProposal("checkin", null, "下午完成了报告", null, null);

    var cards = service.createCandidates("conversation-1", "m1", "下午完成了报告", List.of(suggestion));
    assertEquals(1, cards.size());
    assertTrue(cards.get(0).payloadJson().contains("上午开会\\n下午完成了报告"));
    verifyNoInteractions(checkinJobs);
    when(mapper.lockSourceConversation("p1", "u1")).thenReturn("conversation-1");
    AgentProposalEntity pending = pending("checkin", cards.get(0).payloadJson());
    when(mapper.findOwned("p1", "u1")).thenReturn(pending);
    when(mapper.updatePayload(eq("p1"), eq("u1"), any())).thenReturn(1);

    AgentProposalDto edited = service.updateCheckinPreview("p1",
        new CheckinPreviewUpdateRequest(date.toString(), "上午开会\n下午完成了报告和校对"));
    assertTrue(edited.payloadJson().contains("下午完成了报告和校对"));
    assertTrue(edited.payloadJson().contains("2026-11-03T01:00:00Z"));
    verifyNoInteractions(checkinJobs);
  }

  @Test
  void confirmedCheckinWritesRecordThenQueuesSummaryNotMemory() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(followups.preferences()).thenReturn(new FollowupPreferenceDto(true, 1, 3, "Asia/Shanghai"));
    AgentActionMapper actions = mock(AgentActionMapper.class);
    MemoryService memories = mock(MemoryService.class);
    ProposalService confirming = new ProposalService(mapper, identity, memories,
        new ObjectMapper(), followups, mock(TodoService.class), mock(com.today.punch.PunchService.class),
        mock(com.today.reminder.ReminderService.class), actions, mock(AppointmentService.class), checkins, checkinJobs);
    LocalDate date = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    String payload = "{\"date\":\"" + date + "\",\"text\":\"完成了报告\",\"baseUpdatedAt\":null}";
    AgentProposalEntity pending = pending("checkin", payload);
    AgentProposalEntity confirmed = pending("checkin", payload);
    confirmed.setStatus("confirmed");
    when(mapper.lockSourceConversation("p1", "u1")).thenReturn("conversation-1");
    when(mapper.findOwned("p1", "u1")).thenReturn(pending, confirmed);
    when(mapper.updateStatus(eq("p1"), eq("u1"), eq("confirmed"), any(Instant.class))).thenReturn(1);
    when(checkins.upsertFromAgent("u1", date, "完成了报告", null, null)).thenReturn(
        new CheckinDto("c1", "u1", date.toString(), "完成了报告", "now", "now"));
    when(actions.insert(eq("p1"), eq("u1"), eq("checkin"), eq("c1"),
        eq("完成了报告"), any(Instant.class))).thenReturn(1);

    assertEquals("confirmed", confirming.confirm("p1").status());
    InOrder order = Mockito.inOrder(mapper, checkins, checkinJobs, actions);
    order.verify(mapper).updateStatus(eq("p1"), eq("u1"), eq("confirmed"), any(Instant.class));
    order.verify(checkins).upsertFromAgent("u1", date, "完成了报告", null, null);
    order.verify(checkinJobs).enqueueAndKick("u1", "c1", date.toString(), "完成了报告");
    order.verify(actions).insert(eq("p1"), eq("u1"), eq("checkin"), eq("c1"),
        eq("完成了报告"), any(Instant.class));
    verifyNoInteractions(memories);
  }

  @Test
  void todoEditCandidateRequiresVisibleOwnedTargetAndActualChange() {
    TodoService todos = mock(TodoService.class);
    ProposalService editing = new ProposalService(mapper, identity, mock(MemoryService.class),
        new ObjectMapper(), followups, todos, mock(com.today.punch.PunchService.class),
        mock(com.today.reminder.ReminderService.class), mock(AgentActionMapper.class),
        mock(AppointmentService.class), checkins, checkinJobs);
    when(identity.getCurrentUserId()).thenReturn("u1");
    var suggestion = new ConversationProposal("todo_edit", null, null, null, null,
        "更新旅行清单", null, null, null, null, null, null, "todo-1", false);
    assertTrue(editing.createCandidates("c1", "m1", "请修改旅行清单", List.of(suggestion)).isEmpty());
    when(todos.listForAgent(20)).thenReturn(List.of(todoSnapshot()));
    assertTrue(editing.createCandidates("c1", "m1", "我们聊聊", List.of(suggestion)).isEmpty());
    var unchanged = new ConversationProposal("todo_edit", null, null, null, null,
        "旅行清单", null, null, null, null, null, null, "todo-1", false);
    assertTrue(editing.createCandidates("c1", "m1", "请修改旅行清单", List.of(unchanged)).isEmpty());

    var created = editing.createCandidates("c1", "m1", "请修改旅行清单", List.of(suggestion));
    assertEquals(1, created.size());
    assertTrue(created.get(0).payloadJson().contains("\"targetId\":\"todo-1\""));
    assertTrue(created.get(0).payloadJson().contains("\"updatedAt\":\"2026-11-03T09:00:00Z\""));
    verify(mapper).insert(any());
  }

  @Test
  void confirmedTodoEditUsesGuardedUpdateAndActionLog() {
    TodoService todos = mock(TodoService.class);
    AgentActionMapper actions = mock(AgentActionMapper.class);
    ProposalService editing = new ProposalService(mapper, identity, mock(MemoryService.class),
        new ObjectMapper(), followups, todos, mock(com.today.punch.PunchService.class),
        mock(com.today.reminder.ReminderService.class), actions,
        mock(AppointmentService.class), checkins, checkinJobs);
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(mapper.lockSourceConversation("p1", "u1")).thenReturn("c1");
    String payload = "{\"targetId\":\"todo-1\",\"before\":{\"title\":\"旅行清单\","
        + "\"note\":null,\"dueDate\":null,\"status\":\"open\",\"updatedAt\":\"2026-11-03T09:00:00Z\"},"
        + "\"after\":{\"title\":\"更新旅行清单\",\"note\":null,\"dueDate\":null}}";
    AgentProposalEntity pending = pending("todo_edit", payload);
    AgentProposalEntity confirmed = pending("todo_edit", payload);
    confirmed.setStatus("confirmed");
    when(mapper.findOwned("p1", "u1")).thenReturn(pending, confirmed);
    when(mapper.updateStatus(eq("p1"), eq("u1"), eq("confirmed"), any(Instant.class))).thenReturn(1);
    TodoDto changed = new TodoDto("todo-1", "u1", "更新旅行清单", null, "open", null,
        "2026-11-01T09:00:00Z", "2026-11-03T10:00:00Z", null);
    when(todos.updateFromAgent(eq("todo-1"), any(TodoDto.class), any(TodoUpdateRequest.class))).thenReturn(changed);
    when(actions.insert(eq("p1"), eq("u1"), eq("todo_edit"), eq("todo-1"),
        eq("更新旅行清单"), any(Instant.class))).thenReturn(1);
    when(actions.attachUndoSnapshot(eq("p1"), eq("u1"), any(String.class))).thenReturn(1);

    assertEquals("confirmed", editing.confirm("p1").status());
    InOrder order = Mockito.inOrder(mapper, todos, actions);
    order.verify(mapper).updateStatus(eq("p1"), eq("u1"), eq("confirmed"), any(Instant.class));
    order.verify(todos).updateFromAgent(eq("todo-1"), any(TodoDto.class), any(TodoUpdateRequest.class));
    order.verify(actions).insert(eq("p1"), eq("u1"), eq("todo_edit"), eq("todo-1"),
        eq("更新旅行清单"), any(Instant.class));
    order.verify(actions).attachUndoSnapshot(eq("p1"), eq("u1"), any(String.class));
  }

  private static TodoDto todoSnapshot() {
    return new TodoDto("todo-1", "u1", "旅行清单", null, "open", null,
        "2026-11-01T09:00:00Z", "2026-11-03T09:00:00Z", null);
  }

  private static AgentProposalEntity pending(String kind, String payload) {
    AgentProposalEntity e = new AgentProposalEntity();
    e.setId("p1"); e.setUserId("u1"); e.setSourceMessageId("m1");
    e.setStatus("pending"); e.setKind(kind); e.setPayloadJson(payload);
    e.setCreatedAt(Instant.now());
    return e;
  }
}
