package com.today.checkin;

import com.today.memory.MemoryService;
import com.today.persistence.CheckinAiJobEntity;
import com.today.proactive.ProactiveService;
import com.today.summary.SummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** checkin 提交后的 AI 慢路径：summary → memory → 关闭已回应追问；结果写入 checkin_ai_jobs */
@Service
public class CheckinAiPipeline {

  private static final Logger log = LoggerFactory.getLogger(CheckinAiPipeline.class);

  private final SummaryService summaries;
  private final MemoryService memories;
  private final ProactiveService proactive;
  private final CheckinAiJobMapper jobs;
  private final CheckinService checkins;
  private final CheckinAiJobService jobService;

  public CheckinAiPipeline(
      SummaryService summaries,
      MemoryService memories,
      ProactiveService proactive,
      CheckinAiJobMapper jobs,
      CheckinService checkins,
      @Lazy CheckinAiJobService jobService) {
    this.summaries = summaries;
    this.memories = memories;
    this.proactive = proactive;
    this.jobs = jobs;
    this.checkins = checkins;
    this.jobService = jobService;
  }

  @Async("aiTaskExecutor")
  public void processAfterCheckin(
      String userId, String checkinId, String date, String rawText, String jobId) {
    processJob(checkinId, userId, date, rawText, true);
  }

  /** 同步跑完整流水线（asyncCheckin=false） */
  public void processAfterCheckinSync(
      String userId, String checkinId, String date, String rawText) {
    runPipeline(userId, checkinId, date, resolveRawText(checkinId, rawText));
  }

  /** 调度器已 claim 后调用 */
  public void processClaimedJob(CheckinAiJobEntity job) {
    processJob(
        job.getCheckinId(),
        job.getUserId(),
        job.getCheckinDate().toString(),
        null,
        false);
  }

  private void processJob(
      String checkinId, String userId, String date, String rawText, boolean claimFirst) {
    long start = System.currentTimeMillis();
    CheckinAiJobEntity job = jobs.findByCheckinId(checkinId);
    if (job == null) {
      log.warn("checkin ai job missing checkinId={}", checkinId);
      return;
    }
    if (claimFirst) {
      if (!CheckinAiJobStatus.pending.name().equals(job.getStatus())
          && !CheckinAiJobStatus.failed.name().equals(job.getStatus())) {
        return;
      }
      if (!jobService.claim(job)) {
        return;
      }
      job = jobs.findByCheckinId(checkinId);
      if (job == null) {
        return;
      }
    }

    try {
      String text = resolveRawText(checkinId, rawText);
      runPipeline(userId, checkinId, date, text);
      jobService.markSucceeded(job.getId());
      log.info(
          "checkin ai pipeline ok userId={} checkinId={} jobId={} elapsedMs={}",
          userId,
          checkinId,
          job.getId(),
          System.currentTimeMillis() - start);
    } catch (Exception e) {
      CheckinAiJobEntity latest = jobs.findByCheckinId(checkinId);
      int attempts = latest == null ? 1 : latest.getAttempts();
      int max = latest == null ? 5 : latest.getMaxAttempts();
      jobService.markFailed(job.getId(), attempts, max, e.toString());
      log.error(
          "checkin ai pipeline failed userId={} checkinId={} jobId={} elapsedMs={} reason={}",
          userId,
          checkinId,
          job.getId(),
          System.currentTimeMillis() - start,
          e.toString(),
          e);
    }
  }

  private void runPipeline(String userId, String checkinId, String date, String rawText) {
    summaries.generateForCheckin(userId, checkinId, date, rawText);
    memories.upsertFromCheckin(userId, rawText);
    proactive.markAnsweredFromCheckin(userId, date, rawText);
  }

  private String resolveRawText(String checkinId, String rawText) {
    if (rawText != null && !rawText.isBlank()) {
      return rawText;
    }
    CheckinDto dto = checkins.findByIdForPipeline(checkinId);
    if (dto == null || dto.rawText() == null) {
      throw new IllegalStateException("checkin rawText missing: " + checkinId);
    }
    return dto.rawText();
  }
}
