package com.today.checkin;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CheckinAiJobServiceTest {
  @Test
  void asyncKickWaitsUntilJobIsCommitted() {
    CheckinAiJobMapper jobs = mock(CheckinAiJobMapper.class);
    CheckinAiPipeline pipeline = mock(CheckinAiPipeline.class);
    CheckinAiJobService service = new CheckinAiJobService(jobs, pipeline);
    TransactionSynchronizationManager.initSynchronization();
    try {
      service.enqueueAndKick("u1", "c1", "2026-11-03", "record");
      verify(pipeline, never()).processAfterCheckin(any(), any(), any(), any(), any());

      for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
        sync.afterCommit();
      }
      verify(pipeline).processAfterCheckin(eq("u1"), eq("c1"), eq("2026-11-03"),
          eq("record"), any());
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }
}
