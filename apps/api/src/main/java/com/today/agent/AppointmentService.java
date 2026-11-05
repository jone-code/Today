package com.today.agent;

import com.today.common.EntityMapper;
import com.today.identity.IdentityService;
import com.today.persistence.InboxItemEntity;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AppointmentService {
  private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private final AppointmentMapper mapper;
  private final FollowupMapper inbox;
  private final IdentityService identity;

  public AppointmentService(AppointmentMapper mapper, FollowupMapper inbox, IdentityService identity) {
    this.mapper = mapper;
    this.inbox = inbox;
    this.identity = identity;
  }

  @Transactional
  public AppointmentEntity schedule(String proposalId, String userId, String conversationId,
      String messageId, String title, Instant dueAt, String timezone) {
    Instant now = EntityMapper.now();
    if (dueAt == null || !dueAt.isAfter(now) || dueAt.isAfter(now.plus(Duration.ofDays(365)))
        || title == null || title.isBlank() || title.length() > 200) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "appointment time or title is no longer valid");
    }
    ZoneId.of(timezone);
    AppointmentEntity entity = new AppointmentEntity();
    entity.setId(UUID.randomUUID().toString()); entity.setUserId(userId); entity.setProposalId(proposalId);
    entity.setSourceConversationId(conversationId); entity.setSourceMessageId(messageId);
    entity.setTitle(title.trim()); entity.setDueAt(dueAt); entity.setTimezone(timezone);
    entity.setStatus("pending"); entity.setCreatedAt(now);
    if (mapper.insert(entity) != 1) throw new IllegalStateException("failed to save appointment");
    return entity;
  }

  public List<AppointmentDto> upcoming() {
    return mapper.listUpcoming(identity.getCurrentUserId()).stream().map(this::dto).toList();
  }

  @Transactional
  public void cancel(String id) {
    String userId = identity.getCurrentUserId();
    AppointmentEntity existing = mapper.lockOwned(id, userId);
    if (existing == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "appointment not found");
    if (!"pending".equals(existing.getStatus()) || mapper.cancel(id, userId) != 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "appointment is no longer pending");
    }
  }

  @Transactional
  public int dispatchDue(Instant now) {
    int delivered = 0;
    for (AppointmentEntity due : mapper.listDue(now)) {
      AppointmentEntity appointment = mapper.lockOwned(due.getId(), due.getUserId());
      if (appointment == null || !"pending".equals(appointment.getStatus())
          || appointment.getDueAt().isAfter(now)) continue;
      InboxItemEntity item = new InboxItemEntity();
      item.setId(UUID.randomUUID().toString()); item.setUserId(appointment.getUserId());
      item.setKind("appointment"); item.setSourceKind("one_off_appointment");
      item.setSourceId(appointment.getId());
      item.setBody("约定提醒：" + appointment.getTitle() + "\n原定 "
          + DISPLAY_TIME.format(appointment.getDueAt().atZone(ZoneId.of(appointment.getTimezone())))
          + "（" + appointment.getTimezone() + "）");
      item.setScheduledAt(appointment.getDueAt()); item.setState("unread"); item.setCreatedAt(now);
      inbox.insertInbox(item);
      if (mapper.markDelivered(appointment.getId(), appointment.getUserId(), now) == 1) delivered++;
    }
    return delivered;
  }

  private AppointmentDto dto(AppointmentEntity e) {
    return new AppointmentDto(e.getId(), e.getTitle(), e.getDueAt().toString(),
        e.getTimezone(), e.getStatus(), e.getSourceConversationId());
  }
}
