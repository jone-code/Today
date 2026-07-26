package com.today.checkin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 兜底扫描到期 / 失败可重试的 checkin AI 任务 */
@Component
public class CheckinAiJobScheduler {

  private static final Logger log = LoggerFactory.getLogger(CheckinAiJobScheduler.class);

  private final CheckinAiJobService aiJobs;

  public CheckinAiJobScheduler(CheckinAiJobService aiJobs) {
    this.aiJobs = aiJobs;
  }

  @Scheduled(fixedDelayString = "${today.ai.job-poll-ms:15000}")
  public void tick() {
    try {
      int ran = aiJobs.processDue(8);
      if (ran > 0) {
        log.info("processed {} checkin ai jobs", ran);
      }
    } catch (Exception e) {
      log.warn("checkin ai job tick failed: {}", e.getMessage());
    }
  }
}
