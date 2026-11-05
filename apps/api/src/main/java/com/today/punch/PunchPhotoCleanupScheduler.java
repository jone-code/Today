package com.today.punch;

import com.today.media.LocalMediaStorage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PunchPhotoCleanupScheduler {
  private static final Logger log = LoggerFactory.getLogger(PunchPhotoCleanupScheduler.class);
  private final PunchPhotoCleanupMapper jobs;
  private final PunchMapper punches;
  private final LocalMediaStorage storage;

  public PunchPhotoCleanupScheduler(PunchPhotoCleanupMapper jobs, PunchMapper punches, LocalMediaStorage storage) {
    this.jobs = jobs;
    this.punches = punches;
    this.storage = storage;
  }

  @Scheduled(fixedDelayString = "${today.punch.photo-cleanup-ms:60000}")
  public void tick() {
    try {
      processDue(100);
    } catch (RuntimeException e) {
      log.warn("punch photo cleanup scan failed errorCode={}", e.getClass().getSimpleName());
    }
  }

  public int processDue(int limit) {
    int completed = 0;
    for (PunchPhotoCleanupJob job : jobs.ready(Instant.now(), Math.min(100, Math.max(1, limit)))) {
      Instant lease = Instant.now().plusSeconds(120).truncatedTo(ChronoUnit.MILLIS);
      if (jobs.claim(job.getPath(), Instant.now(), lease) != 1) continue;
      try {
        // A stale job must never remove a photo which is still referenced by a live log.
        if (!punches.isPhotoReferenced(job.getPath(), job.getUserId()) && storage.deleteIfPresent(job.getPath())) {
          completed += jobs.complete(job.getPath(), lease);
        } else {
          retry(job, lease);
        }
      } catch (RuntimeException e) {
        log.warn("punch photo cleanup will retry errorCode={}", e.getClass().getSimpleName());
        retry(job, lease);
      }
    }
    return completed;
  }

  private void retry(PunchPhotoCleanupJob job, Instant lease) {
    int attempts = Math.min(job.getAttempts() + 1, 10);
    jobs.retry(job.getPath(), lease, Instant.now().plusSeconds(Math.min(3600, 5L << attempts)));
  }
}
