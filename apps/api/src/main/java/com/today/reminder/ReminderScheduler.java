package com.today.reminder;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReminderScheduler {

  private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

  private final ReminderService reminderService;

  public ReminderScheduler(ReminderService reminderService) {
    this.reminderService = reminderService;
  }

  /** 每分钟扫描一次到期提醒 */
  @Scheduled(cron = "0 * * * * *")
  public void tick() {
    try {
      int created = reminderService.fireDueReminders(Instant.now());
      if (created > 0) {
        log.info("fired {} reminder deliveries", created);
      }
    } catch (Exception e) {
      log.warn("reminder tick failed: {}", e.getMessage());
    }
  }
}
