package com.today.agent;

import com.today.common.EntityMapper;
import com.today.identity.IdentityService;
import com.today.persistence.FollowupPreferenceEntity;
import com.today.persistence.FollowupTopicEntity;
import com.today.persistence.InboxItemEntity;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FollowupService {
  private final FollowupMapper mapper;
  private final IdentityService identity;
  private final AppointmentMapper appointments;

  public FollowupService(FollowupMapper mapper, IdentityService identity, AppointmentMapper appointments) {
    this.mapper = mapper; this.identity = identity; this.appointments = appointments;
  }

  @Transactional
  public FollowupTopicEntity authorize(String userId, String conversationId, String messageId, String reason, int days) {
    Instant now = EntityMapper.now();
    FollowupTopicEntity topic = new FollowupTopicEntity();
    topic.setId(UUID.randomUUID().toString()); topic.setUserId(userId); topic.setSourceConversationId(conversationId); topic.setSourceMessageId(messageId);
    topic.setReason(reason.trim()); topic.setState("authorized"); topic.setNextEligibleAt(now.plus(Duration.ofDays(Math.max(1, Math.min(days, 30)))));
    topic.setUnansweredCount(0); topic.setCreatedAt(now); topic.setUpdatedAt(now); mapper.insertTopic(topic); return topic;
  }

  public List<InboxItemDto> inbox(int limit) {
    mapper.wakeSnoozed(identity.getCurrentUserId(), EntityMapper.now());
    return mapper.listInbox(identity.getCurrentUserId(), Math.min(100, Math.max(1, limit))).stream().map(this::dto).toList();
  }

  @Transactional
  public int unreadCount() {
    String userId = identity.getCurrentUserId();
    mapper.wakeSnoozed(userId, EntityMapper.now());
    return mapper.countUnread(userId);
  }

  public List<FollowupTopicDto> topics(int limit) {
    return mapper.listUserTopics(identity.getCurrentUserId(), Math.min(100, Math.max(1, limit)))
        .stream().map(this::topicDto).toList();
  }

  @Transactional
  public FollowupTopicDto updateTopic(String id, FollowupTopicUpdateRequest input) {
    String userId = identity.getCurrentUserId();
    if (mapper.lockActiveConversationForTopic(id, userId) == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "topic not found");
    }
    ZoneId zone = input.nextDate() == null ? null : ZoneId.of(preference(userId).getTimezone());
    FollowupTopicEntity topic = mapper.lockTopic(id, userId);
    if (topic == null || !"authorized".equals(topic.getState())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "topic not found or paused");
    }
    String reason = input.reason().trim();
    if (reason.isEmpty() || reason.length() > 200) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid followup reason");
    }
    Instant next = topic.getNextEligibleAt();
    if (input.nextDate() != null) {
      LocalDate date;
      try { date = LocalDate.parse(input.nextDate()); }
      catch (DateTimeParseException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid followup date");
      }
      LocalDate today = LocalDate.now(zone);
      if (!date.isAfter(today) || date.isAfter(today.plusDays(30))) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "followup date must be within the next 30 days");
      }
      next = date.atTime(9, 0).atZone(zone).toInstant();
      if (topic.getLastDeliveredAt() != null
          && next.isBefore(topic.getLastDeliveredAt().plus(Duration.ofDays(7)))) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "same topic requires a seven-day gap");
      }
    }
    if (topic.getReason().equals(reason) && topic.getNextEligibleAt().equals(next)) return topicDto(topic);
    if (!topic.getReason().equals(input.expectedReason())
        || !topic.getNextEligibleAt().toString().equals(input.expectedNextEligibleAt())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "topic changed; refresh before editing");
    }
    topic.setReason(reason);
    topic.setNextEligibleAt(next);
    topic.setUpdatedAt(EntityMapper.now());
    if (mapper.updateTopic(topic) != 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "topic changed; refresh before editing");
    }
    return topicDto(topic);
  }

  @Transactional
  public FollowupPreferenceDto preferences() {
    return preferenceDto(preference(identity.getCurrentUserId()));
  }

  @Transactional
  public FollowupPreferenceDto updatePreferences(FollowupPreferenceUpdateRequest input) {
    FollowupPreferenceEntity pref = preference(identity.getCurrentUserId());
    if (input.enabled() != null) pref.setEnabled(input.enabled());
    if (input.maxPerDay() != null) pref.setMaxPerDay(input.maxPerDay());
    if (input.maxPerWeek() != null) pref.setMaxPerWeek(input.maxPerWeek());
    if (input.timezone() != null) {
      try { pref.setTimezone(ZoneId.of(input.timezone()).getId()); }
      catch (DateTimeException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid timezone"); }
    }
    pref.setUpdatedAt(EntityMapper.now());
    mapper.updatePreference(pref);
    return preferenceDto(pref);
  }

  @Transactional
  public InboxItemDto read(String id) { return updateInbox(id, "read", EntityMapper.now(), null); }

  @Transactional
  public InboxItemDto dismiss(String id) { return updateInbox(id, "dismissed", EntityMapper.now(), null); }

  @Transactional
  public InboxItemDto snooze(String id, Instant until) {
    if (until == null || !until.isAfter(Instant.now()) || until.isAfter(Instant.now().plus(Duration.ofDays(30)))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "snooze must be within 30 days");
    }
    return snoozeAt(id, until);
  }

  @Transactional
  public InboxItemDto snoozeOnDate(String id, String date) {
    LocalDate selected;
    try { selected = LocalDate.parse(date); }
    catch (DateTimeParseException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid snooze date");
    }
    String userId = identity.getCurrentUserId();
    ZoneId zone = ZoneId.of(preference(userId).getTimezone());
    LocalDate today = LocalDate.now(zone);
    if (!selected.isAfter(today) || selected.isAfter(today.plusDays(30))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "snooze date must be within the next 30 days");
    }
    return snoozeAt(id, selected.atTime(9, 0).atZone(zone).toInstant());
  }

  private InboxItemDto snoozeAt(String id, Instant until) {
    String userId = identity.getCurrentUserId();
    InboxItemEntity item = mapper.findInbox(id, userId);
    InboxItemDto result = updateInbox(id, "snoozed", null, until);
    if (item != null && "followup_topic".equals(item.getSourceKind())) {
      mapper.acknowledgeSnoozedTopic(item.getSourceId(), userId, EntityMapper.now());
    }
    return result;
  }

  @Transactional
  public void stopTopic(String id) {
    String userId = identity.getCurrentUserId();
    if (mapper.stopTopic(id, userId, EntityMapper.now()) != 1) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "topic not found or already stopped");
    }
    mapper.dismissTopicInbox(id, userId);
  }

  @Transactional
  public void resumeTopic(String id) {
    String userId = identity.getCurrentUserId();
    Instant now = EntityMapper.now();
    if (mapper.resumeTopic(id, userId, now, now.plus(Duration.ofDays(1))) != 1) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "topic not found or cannot resume");
    }
  }

  @Transactional
  public InboxItemDto continueConversation(String id) {
    String userId = identity.getCurrentUserId();
    InboxItemEntity item = mapper.findInbox(id, userId);
    if (item == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "followup not found");
    }
    if ("one_off_appointment".equals(item.getSourceKind())) {
      AppointmentEntity appointment = appointments.findOwned(item.getSourceId(), userId);
      if (appointment == null || !"delivered".equals(appointment.getStatus())) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "appointment not found");
      }
      item = mapper.lockInbox(id, userId);
      if (item == null || !List.of("unread", "read", "snoozed", "handled").contains(item.getState())) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "followup not found");
      }
      if ("handled".equals(item.getState())) return dto(item);
      return updateInbox(id, "handled", EntityMapper.now(), null);
    }
    if (!"followup_topic".equals(item.getSourceKind())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "followup not found");
    }
    if (!List.of("unread", "read", "snoozed", "handled").contains(item.getState())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "followup not found");
    }
    FollowupTopicEntity topic = mapper.lockTopic(item.getSourceId(), userId);
    if (topic == null || !("authorized".equals(topic.getState())
        || (!"handled".equals(item.getState()) && "paused".equals(topic.getState())
            && topic.getUnansweredCount() >= 2))) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "topic not found");
    }
    item = mapper.lockInbox(id, userId);
    if (item == null || !List.of("unread", "read", "snoozed", "handled").contains(item.getState())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "followup not found");
    }
    if ("handled".equals(item.getState())) return dto(item);
    Instant now = EntityMapper.now();
    if ("paused".equals(topic.getState())) {
      if (mapper.resumeAutoPausedFromInbox(topic.getId(), userId, now, now.plus(Duration.ofDays(7))) != 1) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "topic not found");
      }
    } else {
      topic.setUnansweredCount(0);
      topic.setNextEligibleAt(now.plus(Duration.ofDays(7)));
      topic.setUpdatedAt(now);
      mapper.updateTopic(topic);
    }
    return updateInbox(id, "handled", EntityMapper.now(), null);
  }

  /** Called by the scheduler. It is intentionally idempotent via the inbox unique key. */
  @Transactional
  public int dispatchDue(Instant now) {
    int created = 0;
    for (FollowupTopicEntity due : mapper.listDueTopics(now)) {
      FollowupPreferenceEntity pref = preference(due.getUserId());
      FollowupTopicEntity topic = mapper.lockTopic(due.getId(), due.getUserId());
      if (topic == null || !"authorized".equals(topic.getState()) || topic.getNextEligibleAt().isAfter(now)) continue;
      if (topic.getUnansweredCount() >= 2) {
        topic.setState("paused"); topic.setUpdatedAt(now); mapper.updateTopic(topic); continue;
      }
      if (!pref.isEnabled()) {
        deferTopic(topic, now, Duration.ofDays(1));
        continue;
      }
      InboxItemEntity previous = mapper.latestForTopic(topic.getUserId(), topic.getId());
      if (previous != null && ("unread".equals(previous.getState()) || "snoozed".equals(previous.getState()))) {
        deferTopic(topic, now, Duration.ofDays(1));
        continue;
      }
      ZoneId zone;
      try { zone = ZoneId.of(pref.getTimezone()); }
      catch (DateTimeException e) { zone = ZoneId.of("Asia/Shanghai"); }
      LocalDate localDate = now.atZone(zone).toLocalDate();
      Instant day = localDate.atStartOfDay(zone).toInstant();
      Instant week = localDate.minusDays(localDate.getDayOfWeek().getValue() - 1).atStartOfDay(zone).toInstant();
      if (mapper.countDeliveredSinceLocked(topic.getUserId(), day) >= pref.getMaxPerDay()
          || mapper.countDeliveredSinceLocked(topic.getUserId(), week) >= pref.getMaxPerWeek()) {
        topic.setNextEligibleAt(now.plus(Duration.ofHours(1))); topic.setUpdatedAt(now); mapper.updateTopic(topic); continue;
      }
      InboxItemEntity item = new InboxItemEntity(); item.setId(UUID.randomUUID().toString()); item.setUserId(topic.getUserId()); item.setKind("followup"); item.setSourceKind("followup_topic"); item.setSourceId(topic.getId());
      item.setBody("之前你提到：" + topic.getReason() + "\n现在想聊聊后来怎么样了吗？"); item.setScheduledAt(now); item.setState("unread"); item.setCreatedAt(now);
      if (mapper.insertInbox(item) != 1) continue;
      topic.setLastDeliveredAt(now); topic.setUnansweredCount(topic.getUnansweredCount() + 1); topic.setNextEligibleAt(now.plus(Duration.ofDays(7))); topic.setUpdatedAt(now); mapper.updateTopic(topic); created++;
    }
    return created;
  }

  private void deferTopic(FollowupTopicEntity topic, Instant now, Duration delay) {
    topic.setNextEligibleAt(now.plus(delay));
    topic.setUpdatedAt(now);
    mapper.updateTopic(topic);
  }

  private FollowupPreferenceEntity preference(String userId) {
    mapper.ensurePreference(userId, EntityMapper.now());
    return mapper.lockPreference(userId);
  }

  private FollowupPreferenceDto preferenceDto(FollowupPreferenceEntity e) {
    return new FollowupPreferenceDto(e.isEnabled(), e.getMaxPerDay(), e.getMaxPerWeek(), e.getTimezone());
  }

  private FollowupTopicDto topicDto(FollowupTopicEntity topic) {
    return new FollowupTopicDto(topic.getId(), topic.getReason(), topic.getState(),
        topic.getNextEligibleAt().toString(), topic.getCreatedAt().toString());
  }

  private InboxItemDto updateInbox(String id, String state, Instant readAt, Instant snoozedUntil) {
    String userId = identity.getCurrentUserId();
    if (mapper.updateInboxState(id, userId, state, readAt, snoozedUntil) == 0) {
      InboxItemEntity existing = mapper.findInbox(id, userId);
      if (!("read".equals(state) || "dismissed".equals(state))
          || existing == null || !state.equals(existing.getState())) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "inbox item not found");
      }
      return dto(existing);
    }
    return dto(mapper.findInbox(id, userId));
  }
  private InboxItemDto dto(InboxItemEntity e) {
    FollowupTopicEntity topic = "followup_topic".equals(e.getSourceKind())
        ? mapper.findTopic(e.getSourceId(), e.getUserId()) : null;
    String conversationId = topic != null && ("authorized".equals(topic.getState())
        || ("paused".equals(topic.getState()) && topic.getUnansweredCount() >= 2
            && !"dismissed".equals(e.getState())))
        ? topic.getSourceConversationId() : null;
    if ("one_off_appointment".equals(e.getSourceKind())) {
      AppointmentEntity appointment = appointments.findOwned(e.getSourceId(), e.getUserId());
      if (appointment != null && "delivered".equals(appointment.getStatus())) {
        conversationId = appointment.getSourceConversationId();
      }
    }
    return new InboxItemDto(e.getId(), e.getKind(), e.getSourceKind(), e.getSourceId(),
        e.getBody(), e.getScheduledAt().toString(), e.getState(), e.getCreatedAt().toString(),
        conversationId, e.getSnoozedUntil() == null ? null : e.getSnoozedUntil().toString());
  }
}
