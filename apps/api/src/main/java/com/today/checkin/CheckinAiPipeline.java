package com.today.checkin;

import com.today.memory.MemoryService;
import com.today.proactive.ProactiveService;
import com.today.summary.SummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** checkin 提交后的 AI 慢路径：summary → memory → 关闭已回应追问 */
@Service
public class CheckinAiPipeline {

  private static final Logger log = LoggerFactory.getLogger(CheckinAiPipeline.class);

  private final SummaryService summaries;
  private final MemoryService memories;
  private final ProactiveService proactive;

  public CheckinAiPipeline(
      SummaryService summaries, MemoryService memories, ProactiveService proactive) {
    this.summaries = summaries;
    this.memories = memories;
    this.proactive = proactive;
  }

  @Async("aiTaskExecutor")
  public void processAfterCheckin(String userId, String checkinId, String date, String rawText) {
    long start = System.currentTimeMillis();
    try {
      summaries.generateForCheckin(userId, checkinId, date, rawText);
      memories.upsertFromCheckin(userId, rawText);
      proactive.markAnsweredFromCheckin(userId, date, rawText);
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
    proactive.markAnsweredFromCheckin(userId, date, rawText);
  }
}
