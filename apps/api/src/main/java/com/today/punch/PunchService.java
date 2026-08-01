package com.today.punch;

import com.today.common.EntityMapper;
import com.today.identity.IdentityService;
import com.today.media.LocalMediaStorage;
import com.today.persistence.PunchHabitEntity;
import com.today.persistence.PunchLogEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PunchService {

  private final PunchMapper punchMapper;
  private final IdentityService identity;
  private final LocalMediaStorage mediaStorage;

  public PunchService(
      PunchMapper punchMapper, IdentityService identity, LocalMediaStorage mediaStorage) {
    this.punchMapper = punchMapper;
    this.identity = identity;
    this.mediaStorage = mediaStorage;
  }

  public PunchHabitListDto listHabits(String date) {
    String userId = identity.getCurrentUserId();
    LocalDate day = date == null || date.isBlank() ? EntityMapper.todayUtc() : LocalDate.parse(date);
    Map<String, PunchLogEntity> logsByHabit = new HashMap<>();
    for (PunchLogEntity log : punchMapper.listLogsByUserAndDate(userId, day)) {
      logsByHabit.put(log.getHabitId(), log);
    }

    List<PunchHabitDto> items =
        punchMapper.listHabitsByUserId(userId).stream()
            .map(
                h -> {
                  PunchLogEntity log = logsByHabit.get(h.getId());
                  boolean punched = log != null;
                  return toDto(
                      h,
                      punched,
                      calcStreak(h.getId(), day, punched),
                      punched ? mediaStorage.publicUrl(log.getPhotoPath()) : null);
                })
            .toList();
    return new PunchHabitListDto(items, day.toString());
  }

  @Transactional
  public PunchHabitDto createHabit(PunchHabitCreateRequest input) {
    String userId = identity.getCurrentUserId();
    Instant now = EntityMapper.now();
    PunchHabitEntity entity = new PunchHabitEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setUserId(userId);
    entity.setTitle(input.title().trim());
    entity.setDescription(blankToNull(input.description()));
    entity.setEnabled(input.enabled() == null || input.enabled());
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    punchMapper.insertHabit(entity);
    return toDto(entity, false, 0, null);
  }

  @Transactional
  public PunchHabitDto updateHabit(String id, PunchHabitUpdateRequest input) {
    String userId = identity.getCurrentUserId();
    PunchHabitEntity existing = requireOwnedHabit(id, userId);
    if (input.title() != null && !input.title().isBlank()) {
      existing.setTitle(input.title().trim());
    }
    if (input.description() != null) {
      existing.setDescription(blankToNull(input.description()));
    }
    if (input.enabled() != null) {
      existing.setEnabled(input.enabled());
    }
    existing.setUpdatedAt(EntityMapper.now());
    punchMapper.updateHabit(existing);
    LocalDate today = EntityMapper.todayUtc();
    PunchLogEntity log = punchMapper.findLog(id, today);
    boolean punched = log != null;
    return toDto(
        existing,
        punched,
        calcStreak(id, today, punched),
        punched ? mediaStorage.publicUrl(log.getPhotoPath()) : null);
  }

  @Transactional
  public void deleteHabit(String id) {
    String userId = identity.getCurrentUserId();
    int deleted = punchMapper.deleteHabit(id, userId);
    if (deleted == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "habit not found");
    }
  }

  @Transactional
  public PunchLogDto punch(String habitId, PunchToggleRequest input) {
    return punchInternal(habitId, input, null);
  }

  @Transactional
  public PunchLogDto punchWithPhoto(
      String habitId, String date, String note, MultipartFile photo) {
    return punchInternal(habitId, new PunchToggleRequest(date, note), photo);
  }

  private PunchLogDto punchInternal(
      String habitId, PunchToggleRequest input, MultipartFile photo) {
    String userId = identity.getCurrentUserId();
    requireOwnedHabit(habitId, userId);
    LocalDate day =
        input != null && input.date() != null && !input.date().isBlank()
            ? LocalDate.parse(input.date())
            : EntityMapper.todayUtc();
    String note = input == null ? null : blankToNull(input.note());
    PunchLogEntity existing = punchMapper.findLog(habitId, day);
    if (existing != null) {
      if (photo != null && !photo.isEmpty()) {
        String oldPath = existing.getPhotoPath();
        String newPath = mediaStorage.storePunchPhoto(userId, photo);
        existing.setPhotoPath(newPath);
        if (note != null) {
          existing.setNote(note);
        }
        punchMapper.updateLog(existing);
        mediaStorage.deleteIfPresent(oldPath);
      } else if (note != null) {
        existing.setNote(note);
        punchMapper.updateLog(existing);
      }
      return toLogDto(existing);
    }
    PunchLogEntity log = new PunchLogEntity();
    log.setId(UUID.randomUUID().toString());
    log.setHabitId(habitId);
    log.setUserId(userId);
    log.setPunchDate(day);
    log.setNote(note);
    if (photo != null && !photo.isEmpty()) {
      log.setPhotoPath(mediaStorage.storePunchPhoto(userId, photo));
    }
    log.setCreatedAt(EntityMapper.now());
    punchMapper.insertLog(log);
    return toLogDto(log);
  }

  @Transactional
  public void unpunch(String habitId, String date) {
    String userId = identity.getCurrentUserId();
    requireOwnedHabit(habitId, userId);
    LocalDate day =
        date == null || date.isBlank() ? EntityMapper.todayUtc() : LocalDate.parse(date);
    PunchLogEntity existing = punchMapper.findLog(habitId, day);
    if (existing != null) {
      mediaStorage.deleteIfPresent(existing.getPhotoPath());
    }
    punchMapper.deleteLog(habitId, userId, day);
  }

  private int calcStreak(String habitId, LocalDate today, boolean punchedToday) {
    List<PunchLogEntity> logs = punchMapper.listLogsByHabit(habitId, 60);
    Set<LocalDate> days = new HashSet<>();
    for (PunchLogEntity log : logs) {
      days.add(log.getPunchDate());
    }
    LocalDate cursor = punchedToday ? today : today.minusDays(1);
    int streak = 0;
    while (days.contains(cursor)) {
      streak++;
      cursor = cursor.minusDays(1);
    }
    return streak;
  }

  private PunchHabitEntity requireOwnedHabit(String id, String userId) {
    PunchHabitEntity existing = punchMapper.findHabitById(id);
    if (existing == null || !existing.getUserId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "habit not found");
    }
    return existing;
  }

  private String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private PunchHabitDto toDto(
      PunchHabitEntity entity, boolean punchedToday, int streak, String todayPhotoUrl) {
    return new PunchHabitDto(
        entity.getId(),
        entity.getUserId(),
        entity.getTitle(),
        entity.getDescription(),
        entity.isEnabled(),
        entity.getCreatedAt().toString(),
        entity.getUpdatedAt().toString(),
        punchedToday,
        streak,
        todayPhotoUrl);
  }

  private PunchLogDto toLogDto(PunchLogEntity entity) {
    return new PunchLogDto(
        entity.getId(),
        entity.getHabitId(),
        entity.getUserId(),
        entity.getPunchDate().toString(),
        entity.getNote(),
        mediaStorage.publicUrl(entity.getPhotoPath()),
        entity.getCreatedAt().toString());
  }
}
