package com.today.review;

import com.today.checkin.CheckinMapper;
import com.today.common.EntityMapper;
import com.today.common.JsonUtils;
import com.today.identity.IdentityService;
import com.today.persistence.CheckinEntity;
import com.today.persistence.TodoEntity;
import com.today.persistence.WeeklyReviewEntity;
import com.today.todo.TodoMapper;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WeeklyReviewService {
  private static final Duration RUNNING_LEASE = Duration.ofMinutes(15);
  private final WeeklyReviewMapper reviews;
  private final CheckinMapper checkins;
  private final TodoMapper todos;
  private final IdentityService identity;

  public WeeklyReviewService(WeeklyReviewMapper reviews, CheckinMapper checkins, TodoMapper todos,
      IdentityService identity) {
    this.reviews = reviews; this.checkins = checkins; this.todos = todos; this.identity = identity;
  }

  @Transactional
  public WeeklyReviewDto getOrGenerate(String week) {
    String userId = identity.getCurrentUserId();
    LocalDate start = normalizeWeek(week);
    WeeklyReviewEntity existing = reviews.find(userId, start);
    if (existing != null && "succeeded".equals(existing.getStatus())) return toDto(existing);
    if (existing != null && "running".equals(existing.getStatus()) && !leaseExpired(existing)) return toDto(existing);
    if (existing == null) {
      existing = new WeeklyReviewEntity();
      existing.setId(UUID.randomUUID().toString()); existing.setUserId(userId); existing.setWeekStart(start);
      existing.setCreatedAt(EntityMapper.now()); existing.setAttempts(0); reviews.insert(existing);
    }
    return generate(existing, userId, start);
  }

  @Transactional
  public WeeklyReviewDto retry(String week) {
    String userId = identity.getCurrentUserId(); LocalDate start = normalizeWeek(week);
    WeeklyReviewEntity existing = reviews.find(userId, start);
    if (existing == null) return getOrGenerate(week);
    if (!"failed".equals(existing.getStatus()) && !"expired".equals(existing.getStatus())
        && !("running".equals(existing.getStatus()) && leaseExpired(existing))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "weekly review is not retryable");
    }
    return generate(existing, userId, start);
  }

  private WeeklyReviewDto generate(WeeklyReviewEntity entity, String userId, LocalDate start) {
    entity.setStatus("running"); entity.setAttempts(entity.getAttempts() + 1); entity.setLastError(null);
    entity.setLockedAt(EntityMapper.now()); entity.setUpdatedAt(EntityMapper.now()); reviews.update(entity);
    try {
      LocalDate end = start.plusDays(7);
      List<CheckinEntity> records = checkins.listByUserIdAndRange(userId, start, end);
      List<TodoEntity> done = todos.listCompletedByUserIdAndRange(userId, start, end);
      List<String> highlights = new ArrayList<>();
      for (CheckinEntity record : records) {
        String text = record.getRawText() == null ? "" : record.getRawText().trim().replaceAll("\\s+", " ");
        if (!text.isBlank()) highlights.add(text.length() > 96 ? text.substring(0, 96) + "…" : text);
        if (highlights.size() == 3) break;
      }
      String reflection = records.isEmpty()
          ? "这周还没有留下每日记录，下周可以从一句话开始。"
          : "这周记录了 " + records.size() + " 天，完成了 " + done.size() + " 件待办。愿你带着这些进展继续往前。";
      WeeklyReviewPayload payload = new WeeklyReviewPayload(records.size(), done.size(), highlights, reflection);
      entity.setBodyJson(JsonUtils.toJson(payload)); entity.setStatus("succeeded"); entity.setGeneratedAt(EntityMapper.now());
      entity.setLockedAt(null); entity.setUpdatedAt(EntityMapper.now()); reviews.update(entity);
      return toDto(entity);
    } catch (RuntimeException failure) {
      entity.setStatus("failed"); entity.setLastError(trim(failure.getMessage())); entity.setLockedAt(null); entity.setUpdatedAt(EntityMapper.now());
      reviews.update(entity); throw failure;
    }
  }

  private static LocalDate normalizeWeek(String value) {
    try {
      LocalDate date = LocalDate.parse(value);
      return date.with(DayOfWeek.MONDAY);
    } catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "week must be YYYY-MM-DD"); }
  }
  private static String trim(String value) { return value == null ? "unknown" : value.length() > 512 ? value.substring(0, 512) : value; }

  private static boolean leaseExpired(WeeklyReviewEntity entity) {
    return entity.getLockedAt() == null
        || entity.getLockedAt().plus(RUNNING_LEASE).isBefore(Instant.now());
  }

  private WeeklyReviewDto toDto(WeeklyReviewEntity entity) {
    WeeklyReviewPayload payload = entity.getBodyJson() == null ? new WeeklyReviewPayload(0, 0, List.of(), "")
        : JsonUtils.fromJson(entity.getBodyJson(), WeeklyReviewPayload.class);
    return new WeeklyReviewDto(entity.getWeekStart().toString(), entity.getStatus(), entity.getAttempts(),
        entity.getLastError(), payload.daysRecorded(), payload.completedTodos(), payload.highlights(), payload.reflection(),
        entity.getGeneratedAt(), "failed".equals(entity.getStatus()) || "expired".equals(entity.getStatus()));
  }
  private record WeeklyReviewPayload(int daysRecorded, int completedTodos, List<String> highlights, String reflection) {}
}
