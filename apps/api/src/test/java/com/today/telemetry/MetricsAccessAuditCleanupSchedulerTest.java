package com.today.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MetricsAccessAuditCleanupSchedulerTest {
  @Test void cleanupDeletesOnlyExpiredMetricsAuditRows() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(db.update(contains("metrics_access_audit"), any(Instant.class))).thenReturn(4);
    var scheduler = new MetricsAccessAuditCleanupScheduler(db, 180);

    assertEquals(4, scheduler.cleanupAndReturnForTest());
    verify(db).update(contains("DELETE FROM metrics_access_audit"), any(Instant.class));
  }

  @Test void cleanupFailureIsRetriedByTheNextScheduledRun() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(db.update(contains("metrics_access_audit"), any(Instant.class)))
        .thenThrow(new IllegalStateException("database unavailable"));
    var scheduler = new MetricsAccessAuditCleanupScheduler(db, 180);

    assertEquals(0, scheduler.cleanupAndReturnForTest());
    verify(db).update(contains("DELETE FROM metrics_access_audit"), any(Instant.class));
    verifyNoMoreInteractions(db);
  }
}
