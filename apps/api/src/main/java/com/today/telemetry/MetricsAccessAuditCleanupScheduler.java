package com.today.telemetry;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MetricsAccessAuditCleanupScheduler {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MetricsAccessAuditCleanupScheduler.class);
  private final JdbcTemplate db;
  private final int retainDays;

  public MetricsAccessAuditCleanupScheduler(JdbcTemplate db,
      @Value("${today.metrics.audit-retain-days:180}") int retainDays) {
    this.db = db;
    this.retainDays = Math.max(1, retainDays);
  }

  @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
  public void cleanup() {
    cleanupAndReturnForTest();
  }

  int cleanupAndReturnForTest() {
    try {
      int deleted = db.update("DELETE FROM metrics_access_audit WHERE accessed_at < ?",
          Instant.now().minus(retainDays, ChronoUnit.DAYS));
      log.info("metrics access audit cleanup deleted={} retainDays={}", deleted, retainDays);
      return deleted;
    } catch (RuntimeException failure) {
      log.warn("metrics access audit cleanup failed errorCode={}", failure.getClass().getSimpleName());
      return 0;
    }
  }
}
