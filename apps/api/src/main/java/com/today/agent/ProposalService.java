package com.today.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.today.common.EntityMapper;
import com.today.common.JsonUtils;
import com.today.common.MemoryCategory;
import com.today.checkin.CheckinAiJobService;
import com.today.checkin.CheckinDto;
import com.today.checkin.CheckinService;
import com.today.identity.IdentityService;
import com.today.memory.MemoryService;
import com.today.persistence.AgentProposalEntity;
import com.today.punch.PunchHabitCreateRequest;
import com.today.punch.PunchHabitDto;
import com.today.punch.PunchHabitUpdateRequest;
import com.today.punch.PunchService;
import com.today.punch.PunchService.PunchAgentSnapshot;
import com.today.reminder.ReminderCreateRequest;
import com.today.reminder.ReminderDto;
import com.today.reminder.ReminderService;
import com.today.reminder.ReminderUpdateRequest;
import com.today.todo.TodoCreateRequest;
import com.today.todo.TodoDto;
import com.today.todo.TodoService;
import com.today.todo.TodoUpdateRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProposalService {
  private static final Pattern SPECIFIED_REMINDER_TIME = Pattern.compile(
      "(?:[01]?\\d|2[0-3])[:：][0-5]\\d"
          + "|[0-9零〇一二三四五六七八九十两]{1,3}(?:点|时)(?:半|一刻|三刻|[0-9零〇一二三四五六七八九十]{1,2}分?)?"
          + "|[0-9零〇一二三四五六七八九十两半]{1,3}(?:个)?(?:小时|分钟)后"
          + "|\\b(?:[01]?\\d|2[0-3])\\s*(?:am|pm)\\b"
          + "|\\bin\\s+\\d+\\s+(?:minutes?|hours?)\\b", Pattern.CASE_INSENSITIVE);
  private final AgentProposalMapper mapper;
  private final IdentityService identity;
  private final MemoryService memories;
  private final ObjectMapper objectMapper;
  private final FollowupService followups;
  private final TodoService todos;
  private final PunchService punches;
  private final ReminderService reminders;
  private final AgentActionMapper actions;
  private final AppointmentService appointments;
  private final CheckinService checkins;
  private final CheckinAiJobService checkinJobs;

  public ProposalService(AgentProposalMapper mapper, IdentityService identity, MemoryService memories,
      ObjectMapper objectMapper, FollowupService followups, TodoService todos,
      PunchService punches, ReminderService reminders, AgentActionMapper actions,
      AppointmentService appointments, CheckinService checkins, CheckinAiJobService checkinJobs) {
    this.mapper = mapper;
    this.identity = identity;
    this.memories = memories;
    this.objectMapper = objectMapper;
    this.followups = followups;
    this.todos = todos;
    this.punches = punches;
    this.reminders = reminders;
    this.actions = actions;
    this.appointments = appointments;
    this.checkins = checkins;
    this.checkinJobs = checkinJobs;
  }

  @Transactional
  public List<AgentProposalDto> createCandidates(String conversationId, String sourceMessageId,
      String sourceText, List<ConversationProposal> candidates) {
    String userId = identity.getCurrentUserId();
    List<AgentProposalDto> out = new ArrayList<>();
    boolean declineMemory = declinesMemory(sourceText);
    boolean declineFollowup = declinesFollowup(sourceText);
    if (candidates == null) return out;
    Set<String> kinds = new HashSet<>();
    for (ConversationProposal candidate : candidates.stream().limit(6).toList()) {
      if (candidate == null || candidate.kind() == null || kinds.contains(candidate.kind())) continue;
      if ((declineMemory && "memory".equals(candidate.kind()))
          || (declineFollowup && "followup".equals(candidate.kind()))) continue;
      if ("checkin".equals(candidate.kind())) {
        String entryText = clean(candidate.text(), 1200);
        if (entryText == null) continue;
        LocalDate date = checkinDate(candidate.checkinDate());
        if (date == null) continue;
        Map<String, Object> payload = checkinPreview(date, entryText, sourceText);
        if (payload == null) continue;
        kinds.add("checkin");
        out.add(insert(userId, sourceMessageId, "checkin", toJson(payload)));
      } else if ("memory".equals(candidate.kind())) {
        String text = clean(candidate.text(), 200);
        if (text == null) continue;
        MemoryCategory category;
        try { category = MemoryCategory.valueOf(candidate.category()); }
        catch (IllegalArgumentException | NullPointerException ex) { continue; }
        kinds.add("memory");
        out.add(insert(userId, sourceMessageId, "memory", toJson(Map.of("category", category.name(), "text", text))));
      } else if ("followup".equals(candidate.kind())) {
        String reason = clean(candidate.reason(), 200);
        Integer days = candidate.suggestedDays();
        if (reason == null || days == null || days < 1 || days > 30
            || mapper.hasRejectedFollowupReason(userId, conversationId, reason)) continue;
        kinds.add("followup");
        out.add(insert(userId, sourceMessageId, "followup",
            toJson(Map.of("reason", reason, "suggestedDays", days, "conversationId", conversationId))));
      } else if ("todo".equals(candidate.kind())) {
        String title = clean(candidate.title(), 200);
        String note = optional(candidate.note(), 1000);
        String dueDate = candidate.dueDate();
        if (!explicitTodoIntent(sourceText) || title == null
            || (candidate.note() != null && note == null) || !validDate(dueDate)) continue;
        kinds.add("todo");
        out.add(insert(userId, sourceMessageId, "todo",
            toJson(Map.of("title", title, "note", note == null ? "" : note,
                "dueDate", dueDate == null ? "" : dueDate))));
      } else if ("todo_edit".equals(candidate.kind())) {
        String intent = sourceText == null ? "" : sourceText.toLowerCase(java.util.Locale.ROOT);
        if (!(intent.matches("(?s).*(修改|改成|更改|调整|延期|推迟|改到|change|edit|move).*")
            || intent.contains("换成"))) continue;
        TodoDto before = todos.listForAgent(20).stream()
            .filter(todo -> todo.id().equals(candidate.targetId())).findFirst().orElse(null);
        if (before == null) continue;
        String title = candidate.title() == null ? before.title() : clean(candidate.title(), 200);
        String note = candidate.note() == null ? before.note() : optional(candidate.note(), 1000);
        if ("".equals(note)) note = null;
        if (title == null || (candidate.note() != null && note == null)
            || !validDate(candidate.dueDate())
            || (candidate.dueDate() != null && candidate.dueDate().isEmpty())
            || (Boolean.TRUE.equals(candidate.clearDueDate()) && candidate.dueDate() != null)) continue;
        String dueDate = Boolean.TRUE.equals(candidate.clearDueDate()) ? null
            : candidate.dueDate() == null ? before.dueDate() : candidate.dueDate();
        if (title.equals(before.title()) && java.util.Objects.equals(note, before.note())
            && java.util.Objects.equals(dueDate, before.dueDate())) continue;
        Map<String, Object> original = new HashMap<>();
        original.put("title", before.title()); original.put("note", before.note());
        original.put("dueDate", before.dueDate()); original.put("status", before.status());
        original.put("updatedAt", before.updatedAt());
        Map<String, Object> updated = new HashMap<>();
        updated.put("title", title); updated.put("note", note); updated.put("dueDate", dueDate);
        kinds.add("todo_edit");
        out.add(insert(userId, sourceMessageId, "todo_edit",
            toJson(Map.of("targetId", before.id(), "before", original, "after", updated))));
      } else if ("habit".equals(candidate.kind())) {
        String title = clean(candidate.title(), 128);
        String description = optional(candidate.description(), 512);
        if (!explicitHabitIntent(sourceText) || title == null
            || (candidate.description() != null && description == null)) continue;
        kinds.add("habit");
        out.add(insert(userId, sourceMessageId, "habit",
            toJson(Map.of("title", title, "description", description == null ? "" : description))));
      } else if ("habit_edit".equals(candidate.kind())) {
        String intent = sourceText == null ? "" : sourceText.toLowerCase(java.util.Locale.ROOT);
        if (!intent.matches("(?s).*(修改|改成|更改|调整|换成|暂停|启用|恢复|停用|清空|删除描述|change|edit|pause|resume).*")) continue;
        PunchHabitDto before = punches.listForAgent(20).stream()
            .filter(h -> h.id().equals(candidate.targetId())).findFirst().orElse(null);
        if (before == null || !intent.contains(before.title().toLowerCase(java.util.Locale.ROOT))) continue;
        String title = candidate.title() == null ? before.title() : clean(candidate.title(), 128);
        String description = candidate.description() == null ? before.description() : optional(candidate.description(), 512);
        if ("".equals(description)) description = null;
        boolean enabled = candidate.habitEnabled() == null ? before.enabled() : candidate.habitEnabled();
        if (title == null || (candidate.description() != null && description == null && !candidate.description().isBlank())
            || (!title.equals(before.title()) && !intent.contains(title.toLowerCase(java.util.Locale.ROOT)))
            || (!java.util.Objects.equals(description, before.description()) && description != null
                && !intent.contains(description.toLowerCase(java.util.Locale.ROOT)))
            || (description == null && before.description() != null && candidate.description() != null
                && !intent.matches("(?s).*(清空|删除描述|去掉描述|清除描述|remove description).*"))
            || (!enabled && before.enabled() && !intent.matches("(?s).*(暂停|停用|停止|pause|disable).*"))
            || (enabled && !before.enabled() && !intent.matches("(?s).*(启用|恢复|重新开始|resume|enable).*"))
            || (title.equals(before.title()) && java.util.Objects.equals(description, before.description())
                && enabled == before.enabled())) continue;
        Map<String, Object> after = new HashMap<>();
        after.put("title", title); after.put("description", description); after.put("enabled", enabled);
        kinds.add("habit_edit");
        out.add(insert(userId, sourceMessageId, "habit_edit",
            toJson(Map.of("targetId", before.id(), "before", before, "after", after))));
      } else if ("habit_punch".equals(candidate.kind()) || "habit_unpunch".equals(candidate.kind())) {
        boolean cancel = "habit_unpunch".equals(candidate.kind());
        String intent = sourceText == null ? "" : sourceText.toLowerCase(java.util.Locale.ROOT);
        boolean undoIntent = intent.matches("(?s).*(取消|撤销|撤回).{0,60}打卡.*");
        if (cancel ? !(undoIntent
            || intent.contains("undo check") || intent.contains("uncheck"))
            : !(intent.contains("打卡") || intent.contains("签到") || intent.contains("check in"))
                || undoIntent) continue;
        LocalDate date = checkinDate(candidate.punchDate());
        if (candidate.punchDate() == null || date == null || candidate.targetId() == null) continue;
        boolean visible = punches.listForAgent(20).stream().anyMatch(h -> h.id().equals(candidate.targetId()));
        if (!visible) continue;
        PunchAgentSnapshot snapshot = punches.snapshotForAgent(candidate.targetId(), date);
        if (!intent.contains(snapshot.title().toLowerCase(java.util.Locale.ROOT))) continue;
        if (cancel ? snapshot.logId() == null : !snapshot.enabled() || snapshot.logId() != null) continue;
        kinds.add(candidate.kind());
        out.add(insert(userId, sourceMessageId, candidate.kind(), toJson(snapshot)));
      } else if ("daily_reminder".equals(candidate.kind())) {
        String title = clean(candidate.title(), 128);
        String time = candidate.remindTime();
        String intent = sourceText == null ? "" : sourceText.toLowerCase(java.util.Locale.ROOT);
        if (title == null || time == null || !time.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")
            || !(intent.contains("每天") || intent.contains("每日") || intent.contains("every day"))
            || !explicitReminderIntent(sourceText)) continue;
        kinds.add("daily_reminder");
        out.add(insert(userId, sourceMessageId, "daily_reminder",
            toJson(Map.of("title", title, "remindTime", time,
                "timezone", followups.preferences().timezone()))));
      } else if ("daily_reminder_edit".equals(candidate.kind())) {
        String intent = sourceText == null ? "" : sourceText.toLowerCase(java.util.Locale.ROOT);
        if (!intent.matches("(?s).*(修改|改成|改为|更改|调整|换成|改到|提前|推迟|暂停|启用|恢复|停用|change|edit|pause|resume).*")) continue;
        ReminderDto before = reminders.listForAgent(20).stream()
            .filter(r -> r.id().equals(candidate.targetId())).findFirst().orElse(null);
        if (before == null || !intent.contains(before.title().toLowerCase(java.util.Locale.ROOT))) continue;
        String title = candidate.title() == null ? before.title() : clean(candidate.title(), 128);
        String message = candidate.reminderMessage() == null ? before.message()
            : clean(candidate.reminderMessage(), 512);
        String time = candidate.remindTime() == null ? before.remindTime() : candidate.remindTime();
        String timezone = candidate.reminderTimezone() == null ? before.timezone()
            : candidate.reminderTimezone().trim();
        boolean enabled = candidate.reminderEnabled() == null ? before.enabled() : candidate.reminderEnabled();
        if (title == null || message == null || time == null
            || !time.matches("(?:[01]\\d|2[0-3]):[0-5]\\d") || timezone.length() > 64) continue;
        try { ZoneId.of(timezone); } catch (DateTimeException e) { continue; }
        if ((!title.equals(before.title()) && !intent.contains(title.toLowerCase(java.util.Locale.ROOT)))
            || (!message.equals(before.message()) && !intent.contains(message.toLowerCase(java.util.Locale.ROOT)))
            || (!time.equals(before.remindTime())
                && !intent.matches("(?s).*[0-9零一二三四五六七八九十两]{1,3}[点时:][0-9零一二三四五六七八九十两]{0,2}.*"))
            || (!timezone.equals(before.timezone()) && !intent.matches("(?s).*(时区|当地时间|timezone|time zone).*"))
            || (!enabled && before.enabled() && !intent.matches("(?s).*(暂停|停用|停止|pause|disable).*"))
            || (enabled && !before.enabled() && !intent.matches("(?s).*(启用|恢复|重新开始|resume|enable).*"))
            || (title.equals(before.title()) && message.equals(before.message())
                && time.equals(before.remindTime()) && timezone.equals(before.timezone())
                && enabled == before.enabled())) continue;
        Map<String, Object> after = Map.of("title", title, "message", message,
            "remindTime", time, "timezone", timezone, "enabled", enabled);
        kinds.add("daily_reminder_edit");
        out.add(insert(userId, sourceMessageId, "daily_reminder_edit",
            toJson(Map.of("targetId", before.id(), "before", before, "after", after))));
      } else if ("one_off_reminder".equals(candidate.kind())) {
        String title = clean(candidate.title(), 200);
        String localTime = candidate.scheduledLocal();
        String intent = sourceText == null ? "" : sourceText.toLowerCase(java.util.Locale.ROOT);
        if (title == null || localTime == null
            || !localTime.matches("\\d{4}-\\d{2}-\\d{2}T(?:[01]\\d|2[0-3]):[0-5]\\d")
            || !explicitReminderIntent(sourceText)
            || !hasSpecifiedReminderTime(sourceText)
            || !(intent.contains("提醒我") || intent.contains("记得提醒")
                || intent.contains("提醒一下") || intent.contains("remind me"))
            || intent.contains("每天") || intent.contains("每日") || intent.contains("every day")) continue;
        ZoneId zone = ZoneId.of(followups.preferences().timezone());
        LocalDateTime local;
        try { local = LocalDateTime.parse(localTime); }
        catch (DateTimeException e) { continue; }
        if (zone.getRules().getValidOffsets(local).size() != 1) continue;
        Instant dueAt = local.atZone(zone).toInstant();
        Instant now = Instant.now();
        if (!dueAt.isAfter(now) || dueAt.isAfter(now.plus(Duration.ofDays(365)))) continue;
        kinds.add("one_off_reminder");
        out.add(insert(userId, sourceMessageId, "one_off_reminder", toJson(Map.of(
            "title", title, "scheduledLocal", localTime, "dueAtUtc", dueAt.toString(),
            "timezone", zone.getId(), "conversationId", conversationId)), dueAt));
      }
    }
    return out;
  }

  @Transactional
  public void withdrawPendingForOptOut(String userId, String conversationId, String sourceText) {
    Instant at = EntityMapper.now();
    if (declinesMemory(sourceText)) mapper.rejectPendingKindByConversation(userId, conversationId, "memory", at);
    if (declinesFollowup(sourceText)) mapper.rejectPendingKindByConversation(userId, conversationId, "followup", at);
  }

  private static String clean(String value, int maxLength) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() || trimmed.length() > maxLength ? null : trimmed;
  }

  private static String optional(String value, int maxLength) {
    if (value == null || value.isBlank()) return "";
    return clean(value, maxLength);
  }

  private static boolean validDate(String date) {
    if (date == null || date.isEmpty()) return true;
    if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
    try { LocalDate.parse(date); return true; }
    catch (DateTimeException e) { return false; }
  }

  private LocalDate checkinDate(String requested) {
    ZoneId zone;
    try { zone = ZoneId.of(followups.preferences().timezone()); }
    catch (DateTimeException e) { zone = ZoneId.of("Asia/Shanghai"); }
    LocalDate today = LocalDate.now(zone);
    try {
      LocalDate date = requested == null || requested.isBlank() ? today : LocalDate.parse(requested);
      return date.isAfter(today) || date.isBefore(today.minusDays(30)) ? null : date;
    } catch (DateTimeException e) {
      return null;
    }
  }

  private Map<String, Object> checkinPreview(LocalDate date, String entryText, String source) {
    CheckinDto existing = checkins.getByDate(date);
    String merged = existing == null || existing.rawText().isBlank()
        ? entryText : existing.rawText() + "\n" + entryText;
    if (merged.length() > 2000) return null;
    Map<String, Object> payload = new HashMap<>();
    payload.put("date", date.toString());
    payload.put("source", source == null ? "" : source.substring(0, Math.min(240, source.length())));
    payload.put("entryText", entryText);
    payload.put("previousText", existing == null ? "" : existing.rawText());
    payload.put("text", merged);
    payload.put("baseUpdatedAt", existing == null ? null : existing.updatedAt());
    return payload;
  }

  @Transactional
  public AgentProposalDto updateCheckinPreview(String id, CheckinPreviewUpdateRequest input) {
    String userId = identity.getCurrentUserId();
    if (mapper.lockSourceConversation(id, userId) == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "proposal not found");
    }
    AgentProposalEntity entity = mapper.findOwned(id, userId);
    if (entity == null || !"checkin".equals(entity.getKind()) || !activePending(entity)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "proposal not found");
    }
    Map<String, Object> payload = JsonUtils.fromJson(entity.getPayloadJson(), Map.class);
    LocalDate date = checkinDate(input.date() == null ? String.valueOf(payload.get("date")) : input.date());
    if (date == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid record date");
    if (input.text() == null || !date.toString().equals(payload.get("date"))) {
      payload = checkinPreview(date, String.valueOf(payload.get("entryText")), String.valueOf(payload.get("source")));
      if (payload == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "record exceeds 2000 characters");
    } else {
      String text = clean(input.text(), 2000);
      if (text == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "record text must be 1-2000 characters");
      payload.put("text", text);
    }
    entity.setPayloadJson(toJson(payload));
    if (mapper.updatePayload(id, userId, entity.getPayloadJson()) != 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "proposal was already handled");
    }
    return toDto(entity);
  }

  public List<AgentProposalDto> pending() {
    return mapper.listPending(identity.getCurrentUserId()).stream().filter(this::activePending).map(this::toDto).toList();
  }

  public List<AgentProposalDto> bySourceMessage(String messageId, String userId) {
    return mapper.listBySourceMessage(messageId, userId).stream().filter(this::activePending).map(this::toDto).toList();
  }

  private boolean activePending(AgentProposalEntity e) {
    return "pending".equals(e.getStatus()) && (e.getExpiresAt() == null || e.getExpiresAt().isAfter(Instant.now()));
  }

  public List<AgentActionDto> actions(int limit) {
    return actions.list(identity.getCurrentUserId(), Math.min(50, Math.max(1, limit))).stream()
        .map(this::toActionDto)
        .toList();
  }

  @Transactional
  public AgentActionDto undoAction(String proposalId) {
    String userId = identity.getCurrentUserId();
    AgentActionEntity action = actions.lockOwned(proposalId, userId);
    if (action == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "action not found");
    if ("undone".equals(action.getResult())) return toActionDto(action);
    if (!"succeeded".equals(action.getResult()) || action.getUndoSnapshotJson() == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "action cannot be undone");
    }
    Map<?, ?> snapshot = JsonUtils.fromJson(action.getUndoSnapshotJson(), Map.class);
    Object before = snapshot.get("before");
    Object after = snapshot.get("after");
    if (before == null || after == null) throw new IllegalStateException("invalid action undo snapshot");
    if ("todo_edit".equals(action.getKind())) {
      TodoDto previous = objectMapper.convertValue(before, TodoDto.class);
      TodoDto applied = objectMapper.convertValue(after, TodoDto.class);
      if (!action.getTargetId().equals(previous.id()) || !previous.id().equals(applied.id())) {
        throw new IllegalStateException("action target does not match undo snapshot");
      }
      todos.updateFromAgent(action.getTargetId(), applied, new TodoUpdateRequest(
          previous.title(), previous.note() == null ? "" : previous.note(),
          previous.dueDate(), null, previous.dueDate() == null));
    } else if ("habit_edit".equals(action.getKind())) {
      PunchHabitDto previous = objectMapper.convertValue(before, PunchHabitDto.class);
      PunchHabitDto applied = objectMapper.convertValue(after, PunchHabitDto.class);
      if (!action.getTargetId().equals(previous.id()) || !previous.id().equals(applied.id())) {
        throw new IllegalStateException("action target does not match undo snapshot");
      }
      punches.updateHabitFromAgent(applied, new PunchHabitUpdateRequest(
          previous.title(), previous.description(), previous.enabled()));
    } else if ("daily_reminder_edit".equals(action.getKind())) {
      ReminderDto previous = objectMapper.convertValue(before, ReminderDto.class);
      ReminderDto applied = objectMapper.convertValue(after, ReminderDto.class);
      if (!action.getTargetId().equals(previous.id()) || !previous.id().equals(applied.id())) {
        throw new IllegalStateException("action target does not match undo snapshot");
      }
      reminders.updateFromAgent(applied, new ReminderUpdateRequest(
          previous.title(), previous.message(), previous.remindTime(),
          previous.timezone(), previous.enabled()));
    } else {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "action cannot be undone");
    }
    if (actions.markUndone(proposalId, userId, EntityMapper.now()) != 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "action was already changed");
    }
    return toActionDto(actions.lockOwned(proposalId, userId));
  }

  private AgentActionDto toActionDto(AgentActionEntity action) {
    return new AgentActionDto(action.getProposalId(), action.getKind(), action.getTargetId(),
        action.getSummary(), action.getResult(), action.getExecutedAt().toString(),
        "succeeded".equals(action.getResult()) && action.getUndoSnapshotJson() != null,
        action.getUndoneAt() == null ? null : action.getUndoneAt().toString());
  }

  @Transactional
  @SuppressWarnings("unchecked")
  public AgentProposalDto confirm(String id) {
    String userId = identity.getCurrentUserId();
    if (mapper.lockSourceConversation(id, userId) == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "proposal not found or already handled");
    }
    AgentProposalEntity entity = mapper.findOwned(id, userId);
    if (entity == null || !"pending".equals(entity.getStatus())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "proposal not found or already handled");
    }
    if (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(Instant.now())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "proposal expired");
    }
    Map<String, Object> payload = JsonUtils.fromJson(entity.getPayloadJson(), Map.class);
    if (!Set.of("checkin", "memory", "followup", "todo", "todo_edit", "habit", "habit_edit", "habit_punch", "habit_unpunch", "daily_reminder", "daily_reminder_edit", "one_off_reminder").contains(entity.getKind())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported proposal kind");
    }
    if (mapper.updateStatus(id, userId, "confirmed", EntityMapper.now()) != 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "proposal was already handled");
    }
    String targetId;
    String undoSnapshot = null;
    if ("checkin".equals(entity.getKind())) {
      LocalDate date = checkinDate(String.valueOf(payload.get("date")));
      if (date == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid record date");
      String text = String.valueOf(payload.get("text"));
      CheckinDto saved = checkins.upsertFromAgent(userId, date, text,
          payload.get("baseUpdatedAt") == null ? null : String.valueOf(payload.get("baseUpdatedAt")),
          payload.get("previousText") == null ? null : String.valueOf(payload.get("previousText")));
      checkinJobs.enqueueAndKick(userId, saved.id(), saved.date(), saved.rawText());
      targetId = saved.id();
    } else if ("memory".equals(entity.getKind())) {
      String category = String.valueOf(payload.getOrDefault("category", "life"));
      String text = String.valueOf(payload.getOrDefault("text", ""));
      MemoryCategory parsed;
      try { parsed = MemoryCategory.valueOf(category); } catch (IllegalArgumentException ex) { parsed = MemoryCategory.life; }
      targetId = memories.confirmFromProposal(userId, parsed, text, "conversation_message", entity.getSourceMessageId()).id();
    } else if ("followup".equals(entity.getKind())) {
      int days = payload.get("suggestedDays") instanceof Number n ? n.intValue() : 7;
      targetId = followups.authorize(userId, String.valueOf(payload.getOrDefault("conversationId", "")), entity.getSourceMessageId(), String.valueOf(payload.getOrDefault("reason", "想继续了解这件事")), days).getId();
    } else if ("todo".equals(entity.getKind())) {
      targetId = todos.create(new TodoCreateRequest(String.valueOf(payload.get("title")),
          String.valueOf(payload.get("note")), nullable(payload.get("dueDate")))).id();
    } else if ("todo_edit".equals(entity.getKind())) {
      String idToEdit = String.valueOf(payload.get("targetId"));
      Map<String, Object> before = (Map<String, Object>) payload.get("before");
      Map<String, Object> after = (Map<String, Object>) payload.get("after");
      TodoDto snapshot = new TodoDto(idToEdit, userId, String.valueOf(before.get("title")),
          (String) before.get("note"), String.valueOf(before.get("status")),
          (String) before.get("dueDate"), "", String.valueOf(before.get("updatedAt")), null);
      TodoDto updated = todos.updateFromAgent(idToEdit, snapshot, new TodoUpdateRequest(
          String.valueOf(after.get("title")), after.get("note") == null ? "" : String.valueOf(after.get("note")),
          (String) after.get("dueDate"), null, after.get("dueDate") == null));
      targetId = updated.id();
      undoSnapshot = undoSnapshot(snapshot, updated);
    } else if ("habit".equals(entity.getKind())) {
      targetId = punches.createHabit(new PunchHabitCreateRequest(String.valueOf(payload.get("title")),
          String.valueOf(payload.get("description")), true)).id();
    } else if ("habit_edit".equals(entity.getKind())) {
      PunchHabitDto before = objectMapper.convertValue(payload.get("before"), PunchHabitDto.class);
      Map<String, Object> after = (Map<String, Object>) payload.get("after");
      PunchHabitDto updated = punches.updateHabitFromAgent(before, new PunchHabitUpdateRequest(
          String.valueOf(after.get("title")), (String) after.get("description"),
          (Boolean) after.get("enabled")));
      targetId = updated.id();
      undoSnapshot = undoSnapshot(before, updated);
    } else if ("habit_punch".equals(entity.getKind()) || "habit_unpunch".equals(entity.getKind())) {
      PunchAgentSnapshot snapshot = objectMapper.convertValue(payload, PunchAgentSnapshot.class);
      targetId = punches.applyAgentPunch(snapshot, "habit_unpunch".equals(entity.getKind()));
    } else if ("daily_reminder".equals(entity.getKind())) {
      String title = String.valueOf(payload.get("title"));
      targetId = reminders.create(new ReminderCreateRequest(title, title, String.valueOf(payload.get("remindTime")),
          String.valueOf(payload.get("timezone")), true)).id();
    } else if ("daily_reminder_edit".equals(entity.getKind())) {
      ReminderDto before = objectMapper.convertValue(payload.get("before"), ReminderDto.class);
      Map<String, Object> after = (Map<String, Object>) payload.get("after");
      ReminderDto updated = reminders.updateFromAgent(before, new ReminderUpdateRequest(
          String.valueOf(after.get("title")), String.valueOf(after.get("message")),
          String.valueOf(after.get("remindTime")), String.valueOf(after.get("timezone")),
          (Boolean) after.get("enabled")));
      targetId = updated.id();
      undoSnapshot = undoSnapshot(before, updated);
    } else if ("one_off_reminder".equals(entity.getKind())) {
      targetId = appointments.schedule(id, userId, String.valueOf(payload.get("conversationId")),
          entity.getSourceMessageId(), String.valueOf(payload.get("title")),
          Instant.parse(String.valueOf(payload.get("dueAtUtc"))),
          String.valueOf(payload.get("timezone"))).getId();
    } else {
      throw new IllegalStateException("unsupported proposal kind");
    }
    String summary = String.valueOf(payload.getOrDefault(
        Set.of("memory", "checkin").contains(entity.getKind()) ? "text"
            : "followup".equals(entity.getKind()) ? "reason" : "todo_edit".equals(entity.getKind()) ? "after" : "title", ""));
    if (Set.of("habit_punch", "habit_unpunch").contains(entity.getKind())) {
      summary = String.valueOf(payload.get("title")) + " " + String.valueOf(payload.get("date"));
    }
    if ("todo_edit".equals(entity.getKind())) {
      Map<String, Object> after = (Map<String, Object>) payload.get("after");
      summary = String.valueOf(after.get("title"));
    }
    if ("habit_edit".equals(entity.getKind())) {
      Map<String, Object> after = (Map<String, Object>) payload.get("after");
      summary = String.valueOf(after.get("title"));
    }
    if ("daily_reminder_edit".equals(entity.getKind())) {
      Map<String, Object> after = (Map<String, Object>) payload.get("after");
      summary = String.valueOf(after.get("title"));
    }
    if (summary.length() > 200) summary = summary.substring(0, 200);
    if (actions.insert(id, userId, entity.getKind(), targetId, summary, EntityMapper.now()) != 1) {
      throw new IllegalStateException("failed to record confirmed action");
    }
    if (undoSnapshot != null && actions.attachUndoSnapshot(id, userId, undoSnapshot) != 1) {
      throw new IllegalStateException("failed to record action undo snapshot");
    }
    return toDto(mapper.findOwned(id, userId));
  }

  private String undoSnapshot(Object before, Object after) {
    return toJson(Map.of("before", before, "after", after));
  }

  @Transactional
  public AgentProposalDto reject(String id) {
    String userId = identity.getCurrentUserId();
    if (mapper.updateStatus(id, userId, "rejected", EntityMapper.now()) != 1) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "proposal not found or already handled");
    }
    return toDto(mapper.findOwned(id, userId));
  }

  public void rejectPendingByMessage(String messageId, String userId) {
    for (AgentProposalEntity e : mapper.listBySourceMessage(messageId, userId)) {
      if ("pending".equals(e.getStatus())) mapper.updateStatus(e.getId(), userId, "rejected", EntityMapper.now());
    }
  }

  private AgentProposalDto insert(String userId, String sourceMessageId, String kind, String payload) {
    return insert(userId, sourceMessageId, kind, payload, null);
  }

  private AgentProposalDto insert(String userId, String sourceMessageId, String kind, String payload, Instant expiresAt) {
    AgentProposalEntity e = new AgentProposalEntity();
    e.setId(UUID.randomUUID().toString()); e.setUserId(userId); e.setSourceMessageId(sourceMessageId);
    e.setKind(kind); e.setPayloadJson(payload); e.setStatus("pending");
    e.setCreatedAt(EntityMapper.now()); e.setExpiresAt(expiresAt);
    mapper.insert(e);
    return toDto(e);
  }
  private AgentProposalDto toDto(AgentProposalEntity e) {
    return new AgentProposalDto(e.getId(), e.getKind(), e.getPayloadJson(), e.getStatus(), e.getSourceMessageId(), e.getCreatedAt().toString());
  }
  private String toJson(Object value) {
    try { return objectMapper.writeValueAsString(value); }
    catch (JsonProcessingException e) { throw new IllegalStateException("failed to encode proposal", e); }
  }
  private static boolean containsAny(String text, String... phrases) {
    for (String phrase : phrases) if (text.contains(phrase)) return true;
    return false;
  }
  private static boolean explicitTodoIntent(String text) {
    String intent = normalizeOptOut(text);
    return containsAny(intent, "待办", "任务", "清单", "todo", "task")
        && !negatesCreation(intent, "(?:待办|任务|清单|todo|task)")
        && containsAny(intent, "记成", "记个", "记到", "添加", "加入", "加到", "加个", "加一个", "创建", "新建",
            "列为", "列个", "设为", "add", "create", "make");
  }
  private static boolean explicitHabitIntent(String text) {
    String intent = normalizeOptOut(text);
    return containsAny(intent, "习惯", "habit")
        && !negatesCreation(intent, "(?:习惯|habit)")
        && containsAny(intent, "创建", "新建", "添加", "加入", "设为", "设个", "建立",
            "想养成", "帮我养成", "开始养成", "培养一个", "create", "add", "startahabit");
  }
  private static boolean explicitReminderIntent(String text) {
    String intent = normalizeOptOut(text);
    return !negatesCreation(intent, "(?:提醒|remind|reminder)")
        && (containsAny(intent, "提醒我", "提醒一下", "记得提醒", "帮我提醒", "给我提醒",
        "设置提醒", "设个提醒", "创建提醒", "设为提醒", "remindme", "createareminder", "setareminder")
        || intent.matches("(?s).*(帮我|请|给我).{0,16}(创建|建个|加个|设个|设置).{0,16}提醒.*"));
  }
  private static boolean hasSpecifiedReminderTime(String text) {
    return text != null && SPECIFIED_REMINDER_TIME.matcher(text).find();
  }
  private static boolean negatesCreation(String intent, String kind) {
    return intent.matches("(?s).*(?:不要|别|不用|无需|不需要|先不|暂时不)"
        + "[^，。；;,.!?！？]{0,12}(?:添加|加入|创建|新建|建立|设置|设个|记成|记到|加个|提醒|add|create|set|remind)"
        + "[^，。；;,.!?！？]{0,12}" + kind + ".*")
        || intent.matches("(?s).*(?:不要|别|不用|无需|不需要|先不|暂时不)"
            + "[^，。；;,.!?！？]{0,12}" + kind + ".*");
  }
  private static boolean declinesMemory(String text) {
    String normalized = normalizeOptOut(text);
    return containsAny(normalized, "不要记住", "别记住", "不要保存到记忆", "别保存到记忆",
        "不要记下", "别记下", "don'tremember", "donotremember");
  }
  private static boolean declinesFollowup(String text) {
    String normalized = normalizeOptOut(text);
    return containsAny(normalized, "别再问", "不要再问", "别跟进", "不要跟进",
        "别再跟进", "不要再跟进", "别再提", "不要再提", "别再联系", "不要再联系");
  }
  private static String normalizeOptOut(String text) {
    return text == null ? "" : text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
  }
  private static String nullable(Object value) {
    String text = value == null ? "" : String.valueOf(value);
    return text.isBlank() ? null : text;
  }
}
