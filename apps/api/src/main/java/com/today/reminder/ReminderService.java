package com.today.reminder;

import com.today.common.EntityMapper;
import com.today.identity.IdentityService;
import com.today.persistence.ReminderDeliveryEntity;
import com.today.persistence.ReminderEntity;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReminderService {

  private final ReminderMapper reminderMapper;
  private final IdentityService identity;

  public ReminderService(ReminderMapper reminderMapper, IdentityService identity) {
    this.reminderMapper = reminderMapper;
    this.identity = identity;
  }

  public ReminderListDto listMine() {
    String userId = identity.getCurrentUserId();
    List<ReminderDto> items =
        reminderMapper.listByUserId(userId).stream().map(this::toDto).toList();
    return new ReminderListDto(items);
  }

  @Transactional
  public ReminderDto create(ReminderCreateRequest input) {
    String userId = identity.getCurrentUserId();
    Instant now = EntityMapper.now();
    ReminderEntity entity = new ReminderEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setUserId(userId);
    entity.setTitle(input.title().trim());
    entity.setMessage(input.message().trim());
    entity.setRemindTime(input.remindTime());
    entity.setTimezone(normalizeTimezone(input.timezone()));
    entity.setEnabled(input.enabled() == null || input.enabled());
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    reminderMapper.insert(entity);
    return toDto(entity);
  }

  @Transactional
  public ReminderDto update(String id, ReminderUpdateRequest input) {
    String userId = identity.getCurrentUserId();
    ReminderEntity existing = requireOwned(id, userId);
    if (input.title() != null && !input.title().isBlank()) {
      existing.setTitle(input.title().trim());
    }
    if (input.message() != null && !input.message().isBlank()) {
      existing.setMessage(input.message().trim());
    }
    if (input.remindTime() != null) {
      existing.setRemindTime(input.remindTime());
    }
    if (input.timezone() != null && !input.timezone().isBlank()) {
      existing.setTimezone(normalizeTimezone(input.timezone()));
    }
    if (input.enabled() != null) {
      existing.setEnabled(input.enabled());
    }
    existing.setUpdatedAt(EntityMapper.now());
    reminderMapper.update(existing);
    return toDto(existing);
  }

  @Transactional
  public void delete(String id) {
    String userId = identity.getCurrentUserId();
    int deleted = reminderMapper.deleteByIdAndUserId(id, userId);
    if (deleted == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "reminder not found");
    }
  }

  public ReminderDeliveryListDto listDeliveries(int limit) {
    String userId = identity.getCurrentUserId();
    int n = limit > 0 ? Math.min(limit, 100) : 30;
    List<ReminderDeliveryDto> items =
        reminderMapper.listDeliveriesByUserId(userId, n).stream().map(this::toDeliveryDto).toList();
    return new ReminderDeliveryListDto(items);
  }

  @Transactional
  public ReminderDeliveryDto markRead(String deliveryId) {
    String userId = identity.getCurrentUserId();
    Instant now = EntityMapper.now();
    int updated = reminderMapper.markDeliveryRead(deliveryId, userId, now);
    if (updated == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "delivery not found");
    }
    return listDeliveries(100).items().stream()
        .filter(d -> d.id().equals(deliveryId))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "delivery not found"));
  }

  /** 由调度器调用：扫描到期提醒并写入当日投递记录 */
  @Transactional
  public int fireDueReminders(Instant nowUtc) {
    int created = 0;
    for (ReminderEntity reminder : reminderMapper.listEnabled()) {
      ZoneId zone;
      try {
        zone = ZoneId.of(reminder.getTimezone());
      } catch (DateTimeException ex) {
        zone = ZoneId.of("Asia/Shanghai");
      }
      ZonedDateTime localNow = nowUtc.atZone(zone);
      LocalTime remindAt = LocalTime.parse(reminder.getRemindTime());
      LocalTime windowEnd = remindAt.plusMinutes(1);
      LocalTime current = localNow.toLocalTime().withSecond(0).withNano(0);
      boolean due =
          !current.isBefore(remindAt)
              && current.isBefore(windowEnd.isAfter(remindAt) ? windowEnd : LocalTime.MAX);
      if (!due) {
        continue;
      }
      LocalDate fireDate = localNow.toLocalDate();
      if (reminderMapper.findDelivery(reminder.getId(), fireDate) != null) {
        continue;
      }
      ReminderDeliveryEntity delivery = new ReminderDeliveryEntity();
      delivery.setId(UUID.randomUUID().toString());
      delivery.setReminderId(reminder.getId());
      delivery.setUserId(reminder.getUserId());
      delivery.setFireDate(fireDate);
      delivery.setTitle(reminder.getTitle());
      delivery.setMessage(reminder.getMessage());
      delivery.setStatus("pending");
      delivery.setCreatedAt(nowUtc);
      delivery.setReadAt(null);
      reminderMapper.insertDelivery(delivery);
      created++;
    }
    return created;
  }

  private ReminderEntity requireOwned(String id, String userId) {
    ReminderEntity existing = reminderMapper.findById(id);
    if (existing == null || !existing.getUserId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "reminder not found");
    }
    return existing;
  }

  private String normalizeTimezone(String timezone) {
    String tz = timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone.trim();
    try {
      ZoneId.of(tz);
      return tz;
    } catch (DateTimeException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid timezone");
    }
  }

  private ReminderDto toDto(ReminderEntity entity) {
    return new ReminderDto(
        entity.getId(),
        entity.getUserId(),
        entity.getTitle(),
        entity.getMessage(),
        entity.getRemindTime(),
        entity.getTimezone(),
        entity.isEnabled(),
        entity.getCreatedAt().toString(),
        entity.getUpdatedAt().toString());
  }

  private ReminderDeliveryDto toDeliveryDto(ReminderDeliveryEntity entity) {
    return new ReminderDeliveryDto(
        entity.getId(),
        entity.getReminderId(),
        entity.getUserId(),
        entity.getFireDate().toString(),
        entity.getTitle(),
        entity.getMessage(),
        entity.getStatus(),
        entity.getCreatedAt().toString(),
        entity.getReadAt() == null ? null : entity.getReadAt().toString());
  }
}
