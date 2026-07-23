package com.today.checkin;

import com.today.memory.MemoryService;
import com.today.summary.SummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** checkin 提交后的 AI 慢路径：summary → memory（含 embedding） */
@Service
public class CheckinAiPipeline {

  private static final Logger log = LoggerFactory.getLogger(CheckinAiPipeline.class);

  private final SummaryService summaries;
  private final MemoryService memories;

  public CheckinAiPipeline(SummaryService summaries, MemoryService memories) {
    this.summaries = summaries;
    this.memories = memories;
  }

  @Async("aiTaskExecutor")
  public void processAfterCheckin(String userId, String checkinId, String date, String rawText) {
    long start = System.currentTimeMillis();
    try {
      summaries.generateForCheckin(userId, checkinId, date, rawText);
      memories.upsertFromCheckin(userId, rawText);
      log.info(
          "checkin ai pipeline ok userId={} checkinId={} elapsedMs={}",
          userId,
          checkinId,
          System.currentTimeMillis() - start);
    } catch (Exception e) {
      log.error(
          "checkin ai pipeline failed userId={} checkinId={} elapsedMs={} reason={}",
          userId,
          checkinId,
          System.currentTimeMillis() - start,
          e.toString(),
          e);
    }
  }

  /** 同步跑完整流水线（asyncCheckin=false 或测试用） */
  public void processAfterCheckinSync(
      String userId, String checkinId, String date, String rawText) {
    summaries.generateForCheckin(userId, checkinId, date, rawText);
    memories.upsertFromCheckin(userId, rawText);
  }
}
