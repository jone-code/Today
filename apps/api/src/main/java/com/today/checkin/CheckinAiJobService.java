package com.today.checkin;

import com.today.common.EntityMapper;
import com.today.persistence.CheckinAiJobEntity;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CheckinAiJobService {

  private static final Logger log = LoggerFactory.getLogger(CheckinAiJobService.class);
  private static final int DEFAULT_MAX_ATTEMPTS = 5;

  private final CheckinAiJobMapper jobs;
  private final CheckinAiPipeline pipeline;

  public CheckinAiJobService(CheckinAiJobMapper jobs, CheckinAiPipeline pipeline) {
    this.jobs = jobs;
    this.pipeline = pipeline;
  }

  /** 入队并立即异步踢一脚；调度器兜底重试 */
  @Transactional
  public CheckinAiJobDto enqueueAndKick(
      String userId, String checkinId, String checkinDate, String rawText) {
    CheckinAiJobEntity job = enqueue(userId, checkinId, checkinDate);
    pipeline.processAfterCheckin(userId, checkinId, checkinDate, rawText, job.getId());
    return toDto(job);
  }

  @Transactional
  public CheckinAiJobEntity enqueue(String userId, String checkinId, String checkinDate) {
    Instant now = EntityMapper.now();
    CheckinAiJobEntity existing = jobs.findByCheckinId(checkinId);
    CheckinAiJobEntity entity = existing != null ? existing : new CheckinAiJobEntity();
    if (existing == null) {
      entity.setId(UUID.randomUUID().toString());
      entity.setCreatedAt(now);
      entity.setMaxAttempts(DEFAULT_MAX_ATTEMPTS);
    }
    entity.setUserId(userId);
    entity.setCheckinId(checkinId);
    entity.setCheckinDate(LocalDate.parse(checkinDate));
    entity.setStatus(CheckinAiJobStatus.pending.name());
    entity.setAttempts(0);
    entity.setLastError(null);
    entity.setNextRunAt(now);
    entity.setLockedAt(null);
    entity.setUpdatedAt(now);
    jobs.upsert(entity);
    return entity;
  }

  public CheckinAiJobDto findToday(String userId, String date) {
    CheckinAiJobEntity entity =
        jobs.findByUserAndDate(userId, LocalDate.parse(date));
    return entity == null ? null : toDto(entity);
  }

  public CheckinAiJobDto findByCheckinId(String checkinId) {
    CheckinAiJobEntity entity = jobs.findByCheckinId(checkinId);
    return entity == null ? null : toDto(entity);
  }

  @Transactional
  public CheckinAiJobDto requestRetry(String userId, String checkinId) {
    CheckinAiJobEntity entity = jobs.findByCheckinId(checkinId);
    if (entity == null || !userId.equals(entity.getUserId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ai job not found");
    }
    if (CheckinAiJobStatus.succeeded.name().equals(entity.getStatus())) {
      return toDto(entity);
    }
    Instant now = EntityMapper.now();
    jobs.resetForRetry(entity.getId(), CheckinAiJobStatus.pending.name(), now, now);
    CheckinAiJobEntity refreshed = jobs.findByCheckinId(checkinId);
    pipeline.processAfterCheckin(
        refreshed.getUserId(),
        refreshed.getCheckinId(),
        refreshed.getCheckinDate().toString(),
        null,
        refreshed.getId());
    return toDto(refreshed);
  }

  /** 调度器：认领到期任务并同步执行流水线 */
  public int processDue(int limit) {
    Instant now = EntityMapper.now();
    List<CheckinAiJobEntity> due = jobs.listDue(now, Math.max(1, limit));
    int ran = 0;
    for (CheckinAiJobEntity job : due) {
      if (claim(job)) {
        pipeline.processClaimedJob(job);
        ran++;
      }
    }
    return ran;
  }

  public boolean claim(CheckinAiJobEntity job) {
    Instant now = EntityMapper.now();
    int updated =
        jobs.claim(
            job.getId(),
            job.getStatus(),
            CheckinAiJobStatus.running.name(),
            now,
            now);
    return updated == 1;
  }

  public void markSucceeded(String jobId) {
    jobs.markSucceeded(jobId, CheckinAiJobStatus.succeeded.name(), EntityMapper.now());
  }

  public void markFailed(String jobId, int attempts, int maxAttempts, String error) {
    Instant now = EntityMapper.now();
    String status =
        attempts >= maxAttempts
            ? CheckinAiJobStatus.failed.name()
            : CheckinAiJobStatus.failed.name();
    Instant next = now.plus(backoff(attempts));
    String trimmed =
        error == null
            ? "unknown error"
            : (error.length() > 512 ? error.substring(0, 512) : error);
    jobs.markFailed(jobId, status, attempts, trimmed, next, now);
    log.warn(
        "checkin ai job failed id={} attempts={}/{} nextRunAt={} error={}",
        jobId,
        attempts,
        maxAttempts,
        next,
        trimmed);
  }

  static Duration backoff(int attempts) {
    // 5s, 15s, 45s, 2m, 5m…
    long seconds =
        switch (Math.max(1, attempts)) {
          case 1 -> 5;
          case 2 -> 15;
          case 3 -> 45;
          case 4 -> 120;
          default -> 300;
        };
    return Duration.ofSeconds(seconds);
  }

  static CheckinAiJobDto toDto(CheckinAiJobEntity e) {
    return new CheckinAiJobDto(
        e.getId(),
        e.getCheckinId(),
        e.getCheckinDate().toString(),
        e.getStatus(),
        e.getAttempts(),
        e.getMaxAttempts(),
        e.getLastError());
  }
}
