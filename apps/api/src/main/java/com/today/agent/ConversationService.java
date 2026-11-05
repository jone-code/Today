package com.today.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.today.aigateway.AiGatewayService;
import com.today.aigateway.AiGatewayService.AiTask;
import com.today.common.EntityMapper;
import com.today.common.RateLimitService;
import com.today.checkin.CheckinService;
import com.today.identity.IdentityService;
import com.today.memory.MemoryService;
import com.today.persistence.ConversationEntity;
import com.today.todo.TodoService;
import com.today.punch.PunchService;
import com.today.reminder.ReminderService;
import com.today.timeline.TimelineService;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConversationService {
  private static final TypeReference<ConversationPayload> PAYLOAD_TYPE = new TypeReference<>() {};
  private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

  private final ConversationMapper mapper;
  private final IdentityService identity;
  private final AiGatewayService ai;
  private final ProposalService proposals;
  private final MemoryService memories;
  private final FollowupMapper followups;
  private final ConversationMessageStore messageStore;
  private final AgentActionMapper actions;
  private final AppointmentMapper appointments;
  private final AgentProfileService profiles;
  private final TodoService todos;
  private final PunchService punches;
  private final ReminderService reminders;
  private final CheckinService checkins;
  private final TimelineService timeline;
  private final RateLimitService rateLimits;
  @Value("${today.rate-limit.conversation-per-hour:30}") private int conversationPerHour = 30;
  @Value("${today.rate-limit.conversation-per-day:120}") private int conversationPerDay = 120;
  @Value("${today.rate-limit.conversation-create-per-day:60}") private int conversationCreatePerDay = 60;

  public ConversationService(ConversationMapper mapper, IdentityService identity, AiGatewayService ai, ProposalService proposals, MemoryService memories, FollowupMapper followups, ConversationMessageStore messageStore, AgentActionMapper actions, AppointmentMapper appointments, AgentProfileService profiles, TodoService todos, PunchService punches, ReminderService reminders, CheckinService checkins, TimelineService timeline, RateLimitService rateLimits) {
    this.mapper = mapper;
    this.identity = identity;
    this.ai = ai;
    this.proposals = proposals;
    this.memories = memories;
    this.followups = followups;
    this.messageStore = messageStore;
    this.actions = actions;
    this.appointments = appointments;
    this.profiles = profiles;
    this.todos = todos;
    this.punches = punches;
    this.reminders = reminders;
    this.checkins = checkins;
    this.timeline = timeline;
    this.rateLimits = rateLimits;
  }

  @Transactional
  public ConversationDto create(ConversationCreateRequest input) {
    String userId = identity.getCurrentUserId();
    String requestId = input.clientRequestId();
    if (requestId != null && requestId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clientRequestId must not be blank");
    }
    Instant now = EntityMapper.now();
    ConversationEntity entity = new ConversationEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setUserId(userId);
    entity.setClientRequestId(requestId);
    entity.setTitle(input.title() == null || input.title().isBlank() ? "新的对话" : input.title().trim());
    entity.setStatus("active");
    entity.setVersion(1);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    if (requestId == null) {
      mapper.insert(entity);
      rateLimits.check("conversation_create_day", userId, conversationCreatePerDay, Duration.ofDays(1));
      return ConversationDtos.conversation(entity, List.of());
    }
    mapper.insertIdempotent(entity);
    ConversationEntity persisted = mapper.findByClientRequestId(userId, requestId);
    if (persisted == null) throw new IllegalStateException("conversation create not persisted");
    if (persisted.getDeletedAt() != null || !persisted.getTitle().equals(entity.getTitle())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "conversation request already used");
    }
    if (persisted.getId().equals(entity.getId())) {
      rateLimits.check("conversation_create_day", userId, conversationCreatePerDay, Duration.ofDays(1));
    }
    return ConversationDtos.conversation(persisted, mapper.listMessages(persisted.getId(), userId, 1));
  }

  public List<ConversationDto> list(int limit) {
    String userId = identity.getCurrentUserId();
    return mapper.listByUserId(userId, Math.min(50, Math.max(1, limit))).stream()
        .map(e -> ConversationDtos.conversation(e, mapper.listMessages(e.getId(), userId, 1)))
        .toList();
  }

  public ConversationDto get(String id, int limit) {
    String userId = identity.getCurrentUserId();
    ConversationEntity entity = require(id, userId);
    return ConversationDtos.conversation(entity, mapper.listMessages(id, userId, Math.min(100, Math.max(1, limit))));
  }

  public ConversationMessagePage messages(String id, String before, String beforeId, int limit) {
    String userId = identity.getCurrentUserId();
    require(id, userId);
    if ((before == null) != (beforeId == null) || (beforeId != null && !beforeId.matches(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "both cursor fields are required");
    }
    int size = Math.min(50, Math.max(1, limit));
    List<com.today.persistence.ConversationMessageEntity> rows;
    if (before == null) {
      rows = mapper.listMessages(id, userId, size + 1);
    } else {
      Instant cursor;
      try { cursor = Instant.parse(before); }
      catch (java.time.DateTimeException ex) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid message cursor");
      }
      rows = mapper.listMessagesBefore(id, userId, cursor, beforeId, size + 1);
    }
    return new ConversationMessagePage(rows.stream().limit(size).map(ConversationDtos::message).toList(),
        rows.size() > size);
  }

  public ConversationMessageResult send(String conversationId, ConversationMessageRequest input) {
    String userId = identity.getCurrentUserId();
    String body = input.body().trim();
    var start = messageStore.begin(conversationId, userId, body, input.clientMessageId());
    return generate(conversationId, userId, start);
  }

  public ConversationMessageResult retry(String conversationId, String messageId) {
    String userId = identity.getCurrentUserId();
    return generate(conversationId, userId, messageStore.retry(conversationId, userId, messageId));
  }

  private ConversationMessageResult generate(String conversationId, String userId, ConversationMessageStore.Start start) {
    if (start.replay() != null) return start.replay();
    try {
      if (ConversationSafetyPolicy.requiresEmergencyResponse(start.userMessage().getBody())) {
        return messageStore.finish(conversationId, userId, start.userMessage().getClientMessageId(),
            start.userMessage().getGenerationAttempt(), ConversationSafetyPolicy.RESPONSE, "safety", List.of());
      }
      rateLimits.check("conversation_hour", userId, conversationPerHour, Duration.ofHours(1));
      rateLimits.check("conversation_day", userId, conversationPerDay, Duration.ofDays(1));
      var pref = followups.findPreference(userId);
      ZoneId zone = ZoneId.of(pref == null ? "Asia/Shanghai" : pref.getTimezone());
      Instant now = Instant.now();
      LocalDate localDate = now.atZone(zone).toLocalDate();
      AgentProfileDto profile = profiles.getOrCreate();
      List<TodoContext> visibleTodos = todos.listForAgent(20).stream()
          .map(todo -> new TodoContext(todo.id(), todo.title(), todo.note(), todo.dueDate(), todo.status()))
          .toList();
      var visibleHabits = punches.listForAgent(20).stream()
          .map(habit -> new HabitContext(habit.id(), habit.title(), habit.enabled())).toList();
      var visibleReminders = reminders.listForAgent(20).stream()
          .map(reminder -> new ReminderContext(reminder.id(), reminder.title(), reminder.message(),
              reminder.remindTime(), reminder.timezone(), reminder.enabled())).toList();
      var today = checkins.getByDate(localDate);
      var recentTimeline = timeline.list(6).items().stream()
          .filter(item -> !item.date().equals(localDate.toString()))
          .limit(5)
          .map(item -> new TimelineContext(item.date(), item.highlight(),
              truncate(item.oneLiner(), 160)))
          .toList();
      var response = ai.completeRequired(AiTask.conversation,
          Map.ofEntries(
              Map.entry("message", start.userMessage().getBody()),
              Map.entry("history", start.history()),
              Map.entry("memories", memories.confirmedForConversation(10)),
              Map.entry("todos", visibleTodos),
              Map.entry("habits", visibleHabits),
              Map.entry("reminders", visibleReminders),
              Map.entry("todayRecord", today == null ? Map.of() :
                  Map.of("date", today.date(), "text", today.rawText())),
              Map.entry("recentTimeline", recentTimeline),
              Map.entry("agentName", profile.name()),
              Map.entry("agentTone", profile.tone()),
              Map.entry("timezone", zone.getId()),
              Map.entry("nowLocal", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(now.atZone(zone)))),
          PAYLOAD_TYPE);
      if (response.data() == null || response.data().reply() == null || response.data().reply().isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "empty model reply");
      }
      return messageStore.finish(conversationId, userId, start.userMessage().getClientMessageId(),
          start.userMessage().getGenerationAttempt(),
          response.data().reply().trim(), response.provider().name(), response.data().proposals());
    } catch (RuntimeException error) {
      try {
        messageStore.fail(userId, start.userMessage().getId(), start.userMessage().getGenerationAttempt());
      } catch (RuntimeException failedWrite) {
        log.error("failed to mark conversation message as failed id={} errorCode={}",
            start.userMessage().getId(), failedWrite.getClass().getSimpleName());
      }
      throw error;
    }
  }

  @Transactional
  public void delete(String id) {
    String userId = identity.getCurrentUserId();
    if (mapper.lockById(id, userId) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "conversation not found");
    List<String> messageIds = mapper.listMessageIds(id, userId);
    for (String messageId : messageIds) {
      proposals.rejectPendingByMessage(messageId, userId);
      memories.deactivateConversationSource(userId, messageId);
    }
    followups.pauseByConversation(userId, id, EntityMapper.now());
    followups.redactInboxByConversation(userId, id);
    appointments.cancelByConversation(userId, id);
    appointments.redactInboxByConversation(userId, id);
    actions.redactByConversation(userId, id);
    Instant deletedAt = EntityMapper.now();
    mapper.softDeleteMessages(id, userId, deletedAt);
    if (mapper.softDelete(id, userId, deletedAt) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "conversation not found");
  }

  private ConversationEntity requireEntity(String id, String userId) {
    ConversationEntity e = mapper.findById(id, userId);
    if (e == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "conversation not found");
    return e;
  }
  private ConversationEntity require(String id, String userId) { return requireEntity(id, userId); }

  private record ConversationPayload(String reply, List<ConversationProposal> proposals) {}
  private record TodoContext(String id, String title, String note, String dueDate, String status) {}
  private record HabitContext(String id, String title, boolean enabled) {}
  private record ReminderContext(String id, String title, String message, String remindTime,
      String timezone, boolean enabled) {}
  private record TimelineContext(String date, String highlight, String oneLiner) {}

  private static String truncate(String text, int maxLength) {
    return text == null ? "" : text.length() <= maxLength ? text : text.substring(0, maxLength);
  }
}
