package com.today.agent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ConversationRecoveryScheduler {
  private static final Logger log = LoggerFactory.getLogger(ConversationRecoveryScheduler.class);
  private final ConversationMessageStore messages;

  public ConversationRecoveryScheduler(ConversationMessageStore messages) { this.messages = messages; }

  @Scheduled(fixedDelayString = "${today.ai.job-poll-ms:15000}")
  public void recover() {
    try {
      int failed = messages.failStale(Instant.now().minus(10, ChronoUnit.MINUTES));
      if (failed > 0) log.warn("marked {} stale conversation generations as failed", failed);
    } catch (Exception e) {
      log.warn("conversation recovery scan failed: {}", e.getMessage());
    }
  }
}
