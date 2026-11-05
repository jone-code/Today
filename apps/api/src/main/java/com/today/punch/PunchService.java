package com.today.punch;

import com.today.common.EntityMapper;
import com.today.identity.IdentityService;
import com.today.media.LocalMediaStorage;
import com.today.persistence.PunchHabitEntity;
import com.today.persistence.PunchLogEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PunchService {
  private static final Logger log = LoggerFactory.getLogger(PunchService.class);
  private final PunchMapper punchMapper;
  private final IdentityService identity;
  private final LocalMediaStorage mediaStorage;
  private final PunchPhotoCleanupMapper photoCleanup;
  private final PlatformTransactionManager transactions;

  public PunchService(
      PunchMapper punchMapper, IdentityService identity, LocalMediaStorage mediaStorage,
      PunchPhotoCleanupMapper photoCleanup, PlatformTransactionManager transactions) {
    this.punchMapper = punchMapper;
    this.identity = identity;
    this.mediaStorage = mediaStorage;
    this.photoCleanup = photoCleanup;
    this.transactions = transactions;
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

  public List<PunchHabitDto> listForAgent(int limit) {
    String userId = identity.getCurrentUserId();
    return punchMapper.listHabitsByUserId(userId).stream().limit(Math.min(20, Math.max(1, limit)))
        .map(h -> toDto(h, false, 0, null)).toList();
  }

  public PunchAgentSnapshot snapshotForAgent(String habitId, LocalDate date) {
    String userId = identity.getCurrentUserId();
    PunchHabitEntity habit = requireOwnedHabit(habitId, userId);
    PunchLogEntity log = punchMapper.findLog(habitId, date);
    return new PunchAgentSnapshot(habit.getId(), habit.getTitle(), habit.getUpdatedAt().toString(),
        habit.isEnabled(), date.toString(), log == null ? null : log.getId(),
        log == null ? null : fingerprint(log), log != null && log.getPhotoPath() != null);
  }

  @Transactional
  public String applyAgentPunch(PunchAgentSnapshot expected, boolean cancel) {
    String userId = identity.getCurrentUserId();
    PunchHabitEntity habit = punchMapper.lockHabitById(expected.habitId(), userId);
    if (habit == null || !Objects.equals(habit.getTitle(), expected.title())
        || !Objects.equals(habit.getUpdatedAt().toString(), expected.habitUpdatedAt())
        || habit.isEnabled() != expected.enabled()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "habit changed; request a new proposal");
    }
    LocalDate date = LocalDate.parse(expected.date());
    PunchLogEntity log = punchMapper.lockLog(habit.getId(), userId, date);
    if (!Objects.equals(log == null ? null : log.getId(), expected.logId())
        || !Objects.equals(log == null ? null : fingerprint(log), expected.logFingerprint())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "punch changed; request a new proposal");
    }
    if (cancel) {
      if (log == null || punchMapper.deleteLog(habit.getId(), userId, date) != 1) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "punch no longer exists");
      }
      String photoPath = log.getPhotoPath();
      if (photoPath != null) {
        schedulePhotoCleanup(photoPath, userId);
      }
    } else {
      if (!habit.isEnabled() || log != null) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "habit cannot be punched now");
      }
      PunchLogEntity added = new PunchLogEntity();
      added.setId(UUID.randomUUID().toString()); added.setHabitId(habit.getId());
      added.setUserId(userId); added.setPunchDate(date); added.setCreatedAt(EntityMapper.now());
      punchMapper.insertLog(added);
    }
    return habit.getId();
  }

  private static String fingerprint(PunchLogEntity log) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      String value = log.getId() + "\n" + Objects.toString(log.getNote(), "") + "\n"
          + Objects.toString(log.getPhotoPath(), "");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }

  public record PunchAgentSnapshot(String habitId, String title, String habitUpdatedAt,
      boolean enabled, String date, String logId, String logFingerprint, boolean hasPhoto) {}

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
  public PunchHabitDto updateHabitFromAgent(PunchHabitDto before, PunchHabitUpdateRequest input) {
    String userId = identity.getCurrentUserId();
    PunchHabitEntity habit = punchMapper.lockHabitById(before.id(), userId);
    if (habit == null || !userId.equals(before.userId())
        || !Objects.equals(habit.getTitle(), before.title())
        || !Objects.equals(habit.getDescription(), before.description())
        || habit.isEnabled() != before.enabled()
        || !Objects.equals(habit.getUpdatedAt().toString(), before.updatedAt())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "habit changed; request a new proposal");
    }
    habit.setTitle(input.title());
    habit.setDescription(blankToNull(input.description()));
    habit.setEnabled(input.enabled());
    habit.setUpdatedAt(EntityMapper.now());
    if (punchMapper.updateHabit(habit) != 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "habit changed; request a new proposal");
    }
    return toDto(punchMapper.lockHabitById(before.id(), userId), false, 0, null);
  }

  @Transactional
  public void deleteHabit(String id) {
    String userId = identity.getCurrentUserId();
    if (punchMapper.lockHabitById(id, userId) == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "habit not found");
    }
    List<String> photos = punchMapper.listAllLogsByHabit(id, userId).stream()
        .map(PunchLogEntity::getPhotoPath).filter(Objects::nonNull).toList();
    punchMapper.deleteLogsByHabit(id, userId);
    int deleted = punchMapper.deleteHabit(id, userId);
    if (deleted == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "habit not found");
    }
    photos.forEach(photo -> schedulePhotoCleanup(photo, userId));
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
    if (punchMapper.lockHabitById(habitId, userId) == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "habit not found");
    }
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
        deletePhotoOnRollback(newPath, userId);
        existing.setPhotoPath(newPath);
        if (note != null) {
          existing.setNote(note);
        }
        punchMapper.updateLog(existing);
        schedulePhotoCleanup(oldPath, userId);
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
      String newPath = mediaStorage.storePunchPhoto(userId, photo);
      deletePhotoOnRollback(newPath, userId);
      log.setPhotoPath(newPath);
    }
    log.setCreatedAt(EntityMapper.now());
    punchMapper.insertLog(log);
    return toLogDto(log);
  }

  @Transactional
  public void unpunch(String habitId, String date) {
    String userId = identity.getCurrentUserId();
    if (punchMapper.lockHabitById(habitId, userId) == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "habit not found");
    }
    LocalDate day =
        date == null || date.isBlank() ? EntityMapper.todayUtc() : LocalDate.parse(date);
    PunchLogEntity existing = punchMapper.findLog(habitId, day);
    if (existing != null) {
      schedulePhotoCleanup(existing.getPhotoPath(), userId);
    }
    punchMapper.deleteLog(habitId, userId, day);
  }

  private void schedulePhotoCleanup(String path, String userId) {
    if (path == null) return;
    photoCleanup.enqueue(path, userId, EntityMapper.now());
  }

  private void deletePhotoOnRollback(String path, String userId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status == STATUS_COMMITTED) return;
        try {
          if (mediaStorage.deleteIfPresent(path)) return;
        } catch (RuntimeException e) {
          log.warn("rollback photo deletion failed errorCode={}", e.getClass().getSimpleName());
        }
        try {
          TransactionTemplate retry = new TransactionTemplate(transactions);
          retry.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
          retry.executeWithoutResult(tx -> photoCleanup.enqueue(path, userId, EntityMapper.now()));
        } catch (RuntimeException e) {
          log.error("rollback photo cleanup could not be queued errorCode={}", e.getClass().getSimpleName());
        }
      }
    });
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
